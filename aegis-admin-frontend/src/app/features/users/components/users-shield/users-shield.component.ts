import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  NgZone,
  afterNextRender,
  inject,
  viewChild,
} from '@angular/core';
import type * as ThreeNS from 'three';

type Three = typeof ThreeNS;

const SHIELD_PATH = 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z';
const SHIELD_SVG = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="${SHIELD_PATH}"/></svg>`;

const S = 0.2;
const SHIELD_CY = 12;
const SHIELD_H = 20 * S;

const DEPTH = 0.26;
const BEVEL_T = 0.08;
const BEVEL_S = 0.07;
const FRONT_Z = DEPTH / 2 + BEVEL_T;

const REST_YAW = 0.2;
const REST_PITCH = -0.05;
const SETTLE_YAW = 0.34;
const SETTLE_MS = 1800;

interface Finish {
  faceTop: string;
  faceBottom: string;
  edge: number;
  edgeRough: number;
  envIntensity: number;
  keyIntensity: number;
  rimIntensity: number;
}
const FINISH_LIGHT: Finish = {
  faceTop: '#f7f6f4',
  faceBottom: '#d8d6d2',
  edge: 0xdfe1e6,
  edgeRough: 0.42,
  envIntensity: 0.55,
  keyIntensity: 0.9,
  rimIntensity: 0.7,
};
const FINISH_DARK: Finish = {
  faceTop: '#34363d',
  faceBottom: '#1c1d22',
  edge: 0x9aa0ab,
  edgeRough: 0.32,
  envIntensity: 0.5,
  keyIntensity: 0.9,
  rimIntensity: 0.6,
};

/**
 * 3D Glass Depth shield component watermark for the Users view.
 */
@Component({
  selector: 'app-users-shield',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<canvas #canvas aria-hidden="true"></canvas>`,
  styleUrl: './users-shield.component.scss',
})
export class UsersShieldComponent {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly zone = inject(NgZone);
  private readonly destroyRef = inject(DestroyRef);
  private readonly canvasRef = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');

  private THREE?: Three;
  private renderer?: ThreeNS.WebGLRenderer;
  private scene?: ThreeNS.Scene;
  private camera?: ThreeNS.PerspectiveCamera;
  private root?: ThreeNS.Group;
  private shield?: ThreeNS.Group;
  private key?: ThreeNS.DirectionalLight;
  private rim?: ThreeNS.DirectionalLight;
  private capMat?: ThreeNS.MeshPhysicalMaterial;
  private edgeMat?: ThreeNS.MeshPhysicalMaterial;
  private capTex?: ThreeNS.CanvasTexture;
  private pmrem?: ThreeNS.PMREMGenerator;
  private themeObserver?: MutationObserver;
  private resizeObserver?: ResizeObserver;
  private motionQuery?: MediaQueryList;
  private pinTarget?: HTMLElement;
  private raf = 0;
  private settleStart = 0;
  private yawExtra = 0;
  private reduced = false;
  private destroyed = false;
  private ready = false;

  constructor() {
    afterNextRender(() => {
      this.zone.runOutsideAngular(() => void this.init());
    });
    this.destroyRef.onDestroy(() => this.teardown());
  }

  private onVisibility = (): void => {
    if (document.hidden) this.finishSettle();
  };

  private onMotionPref = (e: MediaQueryListEvent): void => {
    this.reduced = e.matches;
    if (this.reduced) this.finishSettle();
  };

  private async init(): Promise<void> {
    try {
      const [THREE, { SVGLoader }, { toCreasedNormals }] = await Promise.all([
        import('three'),
        import('three/examples/jsm/loaders/SVGLoader.js'),
        import('three/examples/jsm/utils/BufferGeometryUtils.js'),
      ]);
      if (this.destroyed) return;
      this.THREE = THREE;
      const finish = this.finish();

      const canvas = this.canvasRef().nativeElement;
      const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
      renderer.setClearColor(0x000000, 0);
      renderer.outputColorSpace = THREE.SRGBColorSpace;
      renderer.toneMapping = THREE.NeutralToneMapping;
      this.renderer = renderer;

      const scene = new THREE.Scene();
      this.scene = scene;

      this.pmrem = new THREE.PMREMGenerator(renderer);
      const studio = this.buildStudio(THREE);
      scene.environment = this.pmrem.fromScene(studio, 0.02).texture;
      studio.traverse((o) => {
        const m = o as ThreeNS.Mesh;
        m.geometry?.dispose();
        (m.material as ThreeNS.Material | undefined)?.dispose();
      });
      scene.environmentIntensity = finish.envIntensity;

      const camera = new THREE.PerspectiveCamera(22, 1, 0.1, 80);
      this.camera = camera;

      const key = new THREE.DirectionalLight(0xfff3e6, finish.keyIntensity);
      key.position.set(-3, 6, 6);
      scene.add(key);
      this.key = key;
      const rim = new THREE.DirectionalLight(0xdbe6ff, finish.rimIntensity);
      rim.position.set(4, 2, -6);
      scene.add(rim);
      this.rim = rim;

      const root = new THREE.Group();
      this.root = root;
      scene.add(root);
      const shield = new THREE.Group();
      this.shield = shield;
      root.add(shield);

      this.edgeMat = new THREE.MeshPhysicalMaterial({
        color: finish.edge,
        metalness: 1,
        roughness: finish.edgeRough,
        clearcoat: 0.3,
        clearcoatRoughness: 0.2,
      });
      this.capMat = new THREE.MeshPhysicalMaterial({
        color: 0xffffff,
        metalness: 0.15,
        roughness: 0.42,
        clearcoat: 0.6,
        clearcoatRoughness: 0.25,
      });
      this.paintFace();
      this.buildShield(THREE, SVGLoader, toCreasedNormals);
      this.buildMark(THREE);

      this.motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
      this.reduced = this.motionQuery.matches;
      this.motionQuery.addEventListener('change', this.onMotionPref);

      this.themeObserver = new MutationObserver(() => this.applyTheme());
      this.themeObserver.observe(document.documentElement, {
        attributes: true,
        attributeFilter: ['data-theme'],
      });

      this.pinTarget = this.host.nativeElement.closest<HTMLElement>('.content-area') ?? undefined;
      this.resizeObserver = new ResizeObserver(() => this.resize());
      this.resizeObserver.observe(this.pinTarget ?? document.documentElement);
      window.addEventListener('resize', this.onWindowResize, { passive: true });
      document.addEventListener('visibilitychange', this.onVisibility);

      this.ready = true;
      this.resize();
      this.host.nativeElement.classList.add('is-live');
      this.beginSettle();
    } catch {
      this.host.nativeElement.classList.add('is-unsupported');
      this.teardown();
    }
  }

  private finish(): Finish {
    return document.documentElement.getAttribute('data-theme') === 'dark' ? FINISH_DARK : FINISH_LIGHT;
  }

  private applyTheme(): void {
    if (!this.ready || !this.THREE) return;
    const f = this.finish();
    this.edgeMat?.color.setHex(f.edge);
    if (this.edgeMat) this.edgeMat.roughness = f.edgeRough;
    if (this.scene) this.scene.environmentIntensity = f.envIntensity;
    if (this.key) this.key.intensity = f.keyIntensity;
    if (this.rim) this.rim.intensity = f.rimIntensity;
    this.paintFace();
    this.renderFrame();
  }

  private buildStudio(THREE: Three): ThreeNS.Scene {
    const env = new THREE.Scene();
    const dome = new THREE.Mesh(
      new THREE.SphereGeometry(30, 48, 24),
      new THREE.MeshBasicMaterial({ side: THREE.BackSide, vertexColors: true, toneMapped: false }),
    );
    const pos = dome.geometry.getAttribute('position');
    const colors = new Float32Array(pos.count * 3);
    for (let i = 0; i < pos.count; i++) {
      const y = pos.getY(i) / 30;
      const v = y > 0 ? 0.32 + y * 0.9 : 0.1 + (1 + y) * 0.22;
      colors.set([v, v, v * 1.03], i * 3);
    }
    dome.geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3));
    env.add(dome);

    const box = (w: number, h: number, x: number, y: number, z: number, level: number, tint = 1): void => {
      const m = new THREE.Mesh(
        new THREE.PlaneGeometry(w, h),
        new THREE.MeshBasicMaterial({ side: THREE.DoubleSide, toneMapped: false }),
      );
      (m.material as ThreeNS.MeshBasicMaterial).color.setRGB(level, level, level * tint);
      m.position.set(x, y, z);
      m.lookAt(0, 0, 0);
      env.add(m);
    };
    box(16, 9, 0, 13, 3, 6.5);
    box(2.6, 15, -12, 1, 5, 9, 1.04);
    box(2.6, 15, 12, 1, 3, 6, 0.9);
    box(20, 7, 0, 2.5, 17, 2.6);
    box(18, 4, 0, -9, 8, 1.1);
    return env;
  }

  private buildShield(
    THREE: Three,
    SVGLoader: typeof import('three/examples/jsm/loaders/SVGLoader.js').SVGLoader,
    toCreasedNormals: typeof import('three/examples/jsm/utils/BufferGeometryUtils.js').toCreasedNormals,
  ): void {
    const parsed = new SVGLoader().parse(SHIELD_SVG);
    const shapes = parsed.paths[0].toShapes();
    const raw = shapes[0].getPoints(40);
    const outline: ThreeNS.Vector2[] = [];
    for (const p of raw) {
      const v = new THREE.Vector2((p.x - 12) * S, (SHIELD_CY - p.y) * S);
      const prev = outline[outline.length - 1];
      if (!prev || prev.distanceTo(v) > 1e-5) outline.push(v);
    }
    if (outline.length > 1 && outline[0].distanceTo(outline[outline.length - 1]) < 1e-5) outline.pop();

    let minX = Infinity;
    let maxX = -Infinity;
    let minY = Infinity;
    let maxY = -Infinity;
    for (const p of outline) {
      minX = Math.min(minX, p.x);
      maxX = Math.max(maxX, p.x);
      minY = Math.min(minY, p.y);
      maxY = Math.max(maxY, p.y);
    }
    const w = maxX - minX;
    const h = maxY - minY;
    const uv = (v: number[], a: number, b: number, c: number): ThreeNS.Vector2[] =>
      [a, b, c].map((k) => new THREE.Vector2((v[k * 3] - minX) / w, (v[k * 3 + 1] - minY) / h));

    let geo: ThreeNS.BufferGeometry = new THREE.ExtrudeGeometry(new THREE.Shape(outline), {
      depth: DEPTH,
      bevelEnabled: true,
      bevelThickness: BEVEL_T,
      bevelSize: BEVEL_S,
      bevelOffset: -BEVEL_S,
      bevelSegments: 8,
      steps: 1,
      curveSegments: 1,
      UVGenerator: {
        generateTopUV: (_g, v, a, b, c) => uv(v, a, b, c),
        generateSideWallUV: () => [
          new THREE.Vector2(0, 0),
          new THREE.Vector2(1, 0),
          new THREE.Vector2(1, 1),
          new THREE.Vector2(0, 1),
        ],
      },
    });
    geo.translate(0, 0, -DEPTH / 2);
    geo = toCreasedNormals(geo, 0.62);
    this.shield!.add(new THREE.Mesh(geo, [this.capMat!, this.edgeMat!]));
  }

  private buildMark(THREE: Three): void {
    const r = S;
    const bar = new THREE.Mesh(new THREE.CapsuleGeometry(r, 4 * S, 12, 24), this.capMat!);
    bar.scale.z = 0.4;
    bar.position.set(0, (SHIELD_CY - 10) * S, FRONT_Z + 0.02);
    const dot = new THREE.Mesh(new THREE.SphereGeometry(r, 32, 16), this.capMat!);
    dot.scale.z = 0.4;
    dot.position.set(0, (SHIELD_CY - 16) * S, FRONT_Z + 0.02);
    this.shield!.add(bar, dot);
  }

  private paintFace(): void {
    const THREE = this.THREE;
    if (!THREE || !this.renderer || !this.capMat) return;
    const f = this.finish();
    const c = document.createElement('canvas');
    c.width = 256;
    c.height = 320;
    const ctx = c.getContext('2d')!;
    const g = ctx.createLinearGradient(0, 0, 0, c.height);
    g.addColorStop(0, f.faceTop);
    g.addColorStop(1, f.faceBottom);
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, c.width, c.height);
    this.capTex?.dispose();
    const tex = new THREE.CanvasTexture(c);
    tex.colorSpace = THREE.SRGBColorSpace;
    tex.anisotropy = this.renderer.capabilities.getMaxAnisotropy();
    this.capTex = tex;
    this.capMat.map = tex;
    this.capMat.needsUpdate = true;
  }

  private onWindowResize = (): void => this.resize();

  private resize(): void {
    if (!this.renderer || !this.camera || !this.root) return;
    const hostEl = this.host.nativeElement;

    const t = this.pinTarget;
    if (t) {
      const r = t.getBoundingClientRect();
      hostEl.style.left = `${r.left}px`;
      hostEl.style.top = `${r.top}px`;
      hostEl.style.width = `${t.clientWidth}px`;
      hostEl.style.height = `${t.clientHeight}px`;
    }

    const w = Math.max(hostEl.clientWidth, 1);
    const h = Math.max(hostEl.clientHeight, 1);
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2, Math.sqrt(3_000_000 / (w * h))));
    this.renderer.setSize(w, h, false);
    this.camera.aspect = w / h;

    const cs = getComputedStyle(hostEl);
    const num = (name: string, fallback: number): number => {
      const v = parseFloat(cs.getPropertyValue(name));
      return Number.isFinite(v) ? v : fallback;
    };
    const fx = num('--shield-x', 0.8);
    const fy = num('--shield-y', 0.6);
    const fh = num('--shield-h', 0.6);

    const visH = SHIELD_H / fh;
    const dist = visH / (2 * Math.tan((this.camera.fov * Math.PI) / 360));
    const visW = visH * this.camera.aspect;
    this.camera.position.set(0, 0, dist);
    this.camera.lookAt(0, 0, 0);
    this.camera.updateProjectionMatrix();
    this.root.position.set((fx - 0.5) * visW, (0.5 - fy) * visH, 0);
    this.renderFrame();
  }

  private beginSettle(): void {
    if (this.reduced || document.hidden) {
      this.yawExtra = 0;
      this.renderFrame();
      return;
    }
    this.yawExtra = SETTLE_YAW;
    this.settleStart = performance.now();
    this.renderFrame();
    const tick = (ts: number): void => {
      this.raf = 0;
      if (this.destroyed) return;
      const k = Math.min((ts - this.settleStart) / SETTLE_MS, 1);
      this.yawExtra = SETTLE_YAW * Math.pow(1 - k, 3);
      this.renderFrame();
      if (k < 1 && !document.hidden) this.raf = requestAnimationFrame(tick);
    };
    this.raf = requestAnimationFrame(tick);
  }

  private finishSettle(): void {
    if (this.raf) cancelAnimationFrame(this.raf);
    this.raf = 0;
    this.yawExtra = 0;
    this.renderFrame();
  }

  private renderFrame(): void {
    if (!this.renderer || !this.scene || !this.camera || !this.shield) return;
    this.shield.rotation.set(REST_PITCH, REST_YAW + this.yawExtra, 0, 'YXZ');
    this.renderer.render(this.scene, this.camera);
  }

  private teardown(): void {
    this.destroyed = true;
    if (this.raf) cancelAnimationFrame(this.raf);
    this.raf = 0;
    document.removeEventListener('visibilitychange', this.onVisibility);
    window.removeEventListener('resize', this.onWindowResize);
    this.motionQuery?.removeEventListener('change', this.onMotionPref);
    this.themeObserver?.disconnect();
    this.resizeObserver?.disconnect();
    this.capTex?.dispose();
    this.scene?.traverse((o) => {
      const mesh = o as ThreeNS.Mesh;
      mesh.geometry?.dispose();
      const m = mesh.material;
      if (Array.isArray(m)) m.forEach((x) => x.dispose());
      else m?.dispose();
    });
    (this.scene?.environment as ThreeNS.Texture | null)?.dispose();
    this.pmrem?.dispose();
    this.renderer?.dispose();
    this.renderer?.forceContextLoss();
    this.renderer = undefined;
    this.scene = undefined;
    this.ready = false;
  }
}
