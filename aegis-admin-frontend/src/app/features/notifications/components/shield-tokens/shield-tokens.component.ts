import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, NgZone, afterNextRender, inject, viewChild } from '@angular/core';
import type * as ThreeNS from 'three';

type Three = typeof ThreeNS;

/** The AegisNotify brand mark, copied verbatim from the sidebar logo (same path as channel-shield). */
const SHIELD_PATH = 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z';
const SHIELD_SVG = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="${SHIELD_PATH}"/></svg>`;

/** SVG units -> geometry units. The shield is 16 x 20 svg units, so 3.2 x 4.0 geometry units. */
const S = 0.2;
const SHIELD_CY = 12;
const SHIELD_H = 20 * S;
const DEPTH = 0.2;
const BEVEL_T = 0.06;
const BEVEL_S = 0.05;

/** Same graphite / chrome finish as the Notification Detail shield (printed ink is part of the finish). */
interface Finish {
  faceTop: string;
  faceBottom: string;
  edge: number;
  edgeRough: number;
}
const FINISH_LIGHT: Finish = { faceTop: '#34363e', faceBottom: '#121316', edge: 0xeef0f5, edgeRough: 0.14 };
const FINISH_DARK: Finish = { faceTop: '#444852', faceBottom: '#1b1c21', edge: 0xd4d9e2, edgeRough: 0.16 };
/** Brand accent variant: a couple of tokens carry the crimson face so the texture reads as Aegis, not generic. */
const CRIMSON = { top: '#f4094c', bottom: '#8a0530' };

/** Tokens that exist; fewer are shown on small viewports. */
const TOKEN_COUNT = 12;
/** Render cadence: the drift is so slow that 30 fps is indistinguishable and halves the GPU cost. */
const FRAME_MS = 1000 / 30;

interface Token {
  group: ThreeNS.Group;
  /** Position as a fraction of the backdrop (0..1), so the layout survives resizes. */
  nx: number;
  ny: number;
  /** Drift in css px per second. */
  vx: number;
  vy: number;
  size: number;
  z: number;
  phase: number;
  omega: number;
  yaw0: number;
  roll0: number;
  /** Per-token materials, so a token can fade out on its own as it drifts behind page text. */
  cap: ThreeNS.MeshPhysicalMaterial;
  edge: ThreeNS.MeshPhysicalMaterial;
  /** Current fade (0..1), eased toward the target the avoid-zones ask for. */
  alpha: number;
}

/** Text that sits straight on the page (not inside an opaque panel) opts in with this attribute. */
const AVOID_ATTR = '[data-shield-avoid]';
/** A token starts fading this far from a text line and is fully gone by the time it touches it. */
const AVOID_FADE_PX = 70;

/** Deterministic PRNG so the arrangement is identical on every visit. */
function rng(seed: number): () => number {
  let s = seed >>> 0;
  return () => {
    s = (Math.imul(s, 1664525) + 1013904223) >>> 0;
    return s / 4294967296;
  };
}

/**
 * A quiet, persistent backdrop of small shield tokens: the brand shield (same extruded silhouette, chrome bevel
 * and studio lighting as the Notification Detail hero) repeated sparsely and drifting very slowly, one for each
 * notification the screen lists. The canvas is fixed to the content area (never over the sidebar or top bar),
 * sits behind the page and is rendered at a low CSS opacity so it reads as a watermark texture.
 */
@Component({
  selector: 'app-shield-tokens',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<canvas #canvas aria-hidden="true"></canvas>`,
  styleUrl: './shield-tokens.component.scss',
  host: { 'aria-hidden': 'true' },
})
export class ShieldTokensComponent {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly zone = inject(NgZone);
  private readonly destroyRef = inject(DestroyRef);
  private readonly canvasRef = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');

  private THREE?: Three;
  private renderer?: ThreeNS.WebGLRenderer;
  private scene?: ThreeNS.Scene;
  private camera?: ThreeNS.PerspectiveCamera;
  private pmrem?: ThreeNS.PMREMGenerator;
  private geometry?: ThreeNS.BufferGeometry;
  private edgeMat?: ThreeNS.MeshPhysicalMaterial;
  private graphiteMat?: ThreeNS.MeshPhysicalMaterial;
  private crimsonMat?: ThreeNS.MeshPhysicalMaterial;
  private textures: ThreeNS.CanvasTexture[] = [];
  private tokens: Token[] = [];
  private area: HTMLElement | null = null;
  private themeObserver?: MutationObserver;
  private resizeObserver?: ResizeObserver;
  private motionQuery?: MediaQueryList;
  private raf = 0;
  private reduced = false;
  private destroyed = false;
  private ready = false;
  private w = 1;
  private h = 1;
  private left = 0;
  private top = 0;
  private dt = 0;
  private clock = 0;
  private lastTs = 0;
  private lastRender = 0;

  constructor() {
    afterNextRender(() => {
      this.zone.runOutsideAngular(() => void this.init());
    });
    this.destroyRef.onDestroy(() => this.teardown());
  }

  private onVisibility = (): void => {
    if (document.hidden) this.stopLoop();
    else this.startLoop();
  };

  private onMotionPref = (e: MediaQueryListEvent): void => {
    this.reduced = e.matches;
    if (this.reduced) {
      this.stopLoop();
      this.clock = 0;
      this.renderFrame();
    } else {
      this.startLoop();
    }
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
      // Transparent canvas: the tokens sit straight on the page background.
      const renderer = new THREE.WebGLRenderer({
        canvas,
        antialias: true,
        alpha: true,
        powerPreference: 'low-power',
      });
      renderer.setClearColor(0x000000, 0);
      renderer.outputColorSpace = THREE.SRGBColorSpace;
      renderer.toneMapping = THREE.NeutralToneMapping;
      renderer.toneMappingExposure = 1;
      this.renderer = renderer;

      const scene = new THREE.Scene();
      this.scene = scene;

      // Studio image-based lighting, same recipe as the Notification Detail shield.
      this.pmrem = new THREE.PMREMGenerator(renderer);
      const studio = this.buildStudio(THREE);
      scene.environment = this.pmrem.fromScene(studio, 0.02).texture;
      studio.traverse((o) => {
        const m = o as ThreeNS.Mesh;
        m.geometry?.dispose();
        (m.material as ThreeNS.Material | undefined)?.dispose();
      });
      scene.environmentIntensity = 1;

      // Key and rim lights from the same directions as the hero (no shadow map: tokens are tiny and sparse).
      const key = new THREE.DirectionalLight(0xfff3e6, 1.4);
      key.position.set(-3, 6, 6);
      scene.add(key);
      const rim = new THREE.DirectionalLight(0xdbe6ff, 1.2);
      rim.position.set(4, 2, -6);
      scene.add(rim);

      this.camera = new THREE.PerspectiveCamera(22, 1, 1, 100);

      this.edgeMat = new THREE.MeshPhysicalMaterial({
        color: finish.edge,
        metalness: 1,
        roughness: finish.edgeRough,
        clearcoat: 0.4,
        clearcoatRoughness: 0.1,
      });
      const capParams = {
        color: 0xffffff,
        metalness: 0.35,
        roughness: 0.34,
        clearcoat: 1,
        clearcoatRoughness: 0.1,
      };
      this.graphiteMat = new THREE.MeshPhysicalMaterial(capParams);
      this.crimsonMat = new THREE.MeshPhysicalMaterial(capParams);
      this.paintFaces();

      this.buildGeometry(THREE, SVGLoader, toCreasedNormals);
      this.buildTokens(THREE);

      this.motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
      this.reduced = this.motionQuery.matches;
      this.motionQuery.addEventListener('change', this.onMotionPref);

      this.themeObserver = new MutationObserver(() => this.applyTheme());
      this.themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });

      // The backdrop is fixed to the routed content area, so it never paints over the sidebar or top bar.
      this.area = this.host.nativeElement.closest<HTMLElement>('.content-area');
      this.resizeObserver = new ResizeObserver(() => this.resize());
      this.resizeObserver.observe(this.area ?? document.documentElement);
      window.addEventListener('resize', this.onWindowResize, { passive: true });
      // The text moves under the fixed backdrop as the page scrolls; the static (reduced-motion) frame must follow.
      this.area?.addEventListener('scroll', this.onScroll, { passive: true });
      document.addEventListener('visibilitychange', this.onVisibility);

      this.ready = true;
      this.resize();
      this.host.nativeElement.classList.add('is-live');
      if (!document.hidden) this.startLoop();
    } catch {
      // No WebGL (or context lost): the page simply has no backdrop.
      this.host.nativeElement.classList.add('is-unsupported');
      this.teardown();
    }
  }

  private onWindowResize = (): void => this.resize();

  private onScroll = (): void => {
    if (this.reduced) this.renderFrame();
  };

  private finish(): Finish {
    return document.documentElement.getAttribute('data-theme') === 'dark' ? FINISH_DARK : FINISH_LIGHT;
  }

  private applyTheme(): void {
    if (!this.ready) return;
    const f = this.finish();
    for (const t of this.tokens) {
      t.edge.color.setHex(f.edge);
      t.edge.roughness = f.edgeRough;
    }
    this.paintFaces();
    this.renderFrame();
  }

  /** Vertical sheen map for the faces (graphite from the theme finish, plus the crimson accent variant). */
  private paintFaces(): void {
    const THREE = this.THREE;
    if (!THREE || !this.graphiteMat || !this.crimsonMat) return;
    const finish = this.finish();
    const make = (top: string, bottom: string): ThreeNS.CanvasTexture => {
      const c = document.createElement('canvas');
      c.width = 4;
      c.height = 256;
      const ctx = c.getContext('2d')!;
      const g = ctx.createLinearGradient(0, 0, 0, 256);
      g.addColorStop(0, top);
      g.addColorStop(1, bottom);
      ctx.fillStyle = g;
      ctx.fillRect(0, 0, 4, 256);
      const tex = new THREE.CanvasTexture(c);
      tex.colorSpace = THREE.SRGBColorSpace;
      return tex;
    };
    this.textures.forEach((t) => t.dispose());
    this.textures = [make(finish.faceTop, finish.faceBottom), make(CRIMSON.top, CRIMSON.bottom)];
    this.graphiteMat.map = this.textures[0];
    this.graphiteMat.needsUpdate = true;
    this.crimsonMat.map = this.textures[1];
    this.crimsonMat.needsUpdate = true;
  }

  /** Dim dome with large emissive softboxes (copied from the hero) so bevels pick up streak highlights. */
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

  /** One shared, single-piece shield extruded from the real brand path (SVGLoader), with the hero's rounded bevel. */
  private buildGeometry(
    THREE: Three,
    SVGLoader: typeof import('three/examples/jsm/loaders/SVGLoader.js').SVGLoader,
    toCreasedNormals: typeof import('three/examples/jsm/utils/BufferGeometryUtils.js').toCreasedNormals,
  ): void {
    const parsed = new SVGLoader().parse(SHIELD_SVG);
    const shapes = parsed.paths[0].toShapes();
    const raw = shapes[0].getPoints(24);
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
      bevelSegments: 5,
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
    this.geometry = geo;
  }

  /** A sparse, well-spaced set of tokens: best-candidate sampling keeps them from clumping. */
  private buildTokens(THREE: Three): void {
    const rand = rng(20260);
    const placed: [number, number][] = [];
    for (let i = 0; i < TOKEN_COUNT; i++) {
      let best: [number, number] = [0.5, 0.5];
      let bestD = -1;
      for (let k = 0; k < 14; k++) {
        const c: [number, number] = [0.03 + rand() * 0.94, 0.04 + rand() * 0.92];
        let d = Infinity;
        for (const p of placed) d = Math.min(d, Math.hypot((c[0] - p[0]) * 1.8, c[1] - p[1]));
        if (placed.length === 0) d = 1;
        if (d > bestD) {
          bestD = d;
          best = c;
        }
      }
      placed.push(best);

      const crimson = i === 3 || i === 8;
      const group = new THREE.Group();
      const cap = (crimson ? this.crimsonMat! : this.graphiteMat!).clone();
      const edge = this.edgeMat!.clone();
      cap.transparent = edge.transparent = true;
      const mesh = new THREE.Mesh(this.geometry!, [cap, edge]);
      group.add(mesh);
      this.scene!.add(group);

      const heading = rand() * Math.PI * 2;
      const speed = 3 + rand() * 4;
      this.tokens.push({
        group,
        nx: best[0],
        ny: best[1],
        vx: Math.cos(heading) * speed,
        vy: Math.sin(heading) * speed,
        size: 54 + rand() * 54,
        z: (rand() - 0.5) * 240,
        phase: rand() * Math.PI * 2,
        omega: 0.12 + rand() * 0.13,
        yaw0: (rand() - 0.5) * 0.7,
        roll0: (rand() - 0.5) * 0.3,
        cap,
        edge,
        alpha: 1,
      });
    }
  }

  private resize(): void {
    if (!this.renderer || !this.camera) return;
    const a = this.area;
    let left = 0;
    let top = 0;
    let w = window.innerWidth;
    let h = window.innerHeight;
    if (a) {
      // Content box of the scroll container (excludes its scrollbar), in viewport coordinates.
      const r = a.getBoundingClientRect();
      left = r.left + a.clientLeft;
      top = r.top + a.clientTop;
      w = a.clientWidth;
      h = a.clientHeight;
    }
    w = Math.max(Math.round(w), 1);
    h = Math.max(Math.round(h), 1);
    const el = this.host.nativeElement;
    el.style.left = `${left}px`;
    el.style.top = `${top}px`;
    el.style.width = `${w}px`;
    el.style.height = `${h}px`;
    this.w = w;
    this.h = h;
    this.left = left;
    this.top = top;

    // Cap the backing store: the tokens are soft and faint, so a modest pixel budget is plenty.
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5, Math.sqrt(3_000_000 / (w * h))));
    this.renderer.setSize(w, h, false);

    // 1 world unit = 1 css px on the z = 0 plane.
    const cam = this.camera;
    cam.aspect = w / h;
    const dist = h / 2 / Math.tan((cam.fov * Math.PI) / 360);
    cam.near = dist * 0.3;
    cam.far = dist * 3;
    cam.position.set(0, 0, dist);
    cam.lookAt(0, 0, 0);
    cam.updateProjectionMatrix();

    // Fewer tokens in a smaller area, so the density stays constant.
    const shown = Math.min(TOKEN_COUNT, Math.max(6, Math.round((w * h) / 120_000)));
    this.tokens.forEach((t, i) => (t.group.visible = i < shown));
    this.renderFrame();
  }

  private startLoop(): void {
    if (this.raf || !this.ready || document.hidden || this.reduced) return;
    this.lastTs = performance.now();
    const tick = (ts: number): void => {
      this.raf = 0;
      if (this.destroyed || document.hidden || this.reduced) return;
      this.raf = requestAnimationFrame(tick);
      if (ts - this.lastRender < FRAME_MS) return;
      this.lastRender = ts;
      const dt = Math.min((ts - this.lastTs) / 1000, 0.1);
      this.lastTs = ts;
      this.clock += dt;
      this.dt = dt;
      this.advance(dt);
      this.renderFrame();
    };
    this.raf = requestAnimationFrame(tick);
  }

  private stopLoop(): void {
    if (this.raf) cancelAnimationFrame(this.raf);
    this.raf = 0;
  }

  /** Slow linear drift, wrapping off one edge and back in at the other. */
  private advance(dt: number): void {
    for (const t of this.tokens) {
      t.nx += (t.vx * dt) / this.w;
      t.ny += (t.vy * dt) / this.h;
      const mx = (t.size * 0.9) / this.w;
      const my = (t.size * 0.9) / this.h;
      if (t.nx < -mx) t.nx = 1 + mx;
      else if (t.nx > 1 + mx) t.nx = -mx;
      if (t.ny < -my) t.ny = 1 + my;
      else if (t.ny > 1 + my) t.ny = -my;
    }
  }

  private renderFrame(): void {
    if (!this.renderer || !this.scene || !this.camera) return;
    const idle = this.reduced ? 0 : 1;
    const c = this.clock;
    const zones = this.avoidZones();
    // Ease the fade while animating; snap to it for the static frame.
    const ease = this.reduced || this.dt === 0 ? 1 : Math.min(this.dt * 3, 1);
    for (const t of this.tokens) {
      if (!t.group.visible) continue;
      const target = this.fadeFor(t, zones);
      t.alpha += (target - t.alpha) * ease;
      const a = t.alpha < 0.01 ? 0 : t.alpha;
      t.cap.opacity = t.edge.opacity = a;
      t.group.children[0].visible = a > 0;
      t.group.position.set((t.nx - 0.5) * this.w, (0.5 - t.ny) * this.h, t.z);
      t.group.scale.setScalar(t.size / SHIELD_H);
      // A gentle turntable sway rather than a spin, so the faces keep facing the viewer.
      t.group.rotation.set(
        -0.06 + Math.sin(c * t.omega * 0.7 + t.phase * 1.3) * 0.16 * idle,
        t.yaw0 + Math.sin(c * t.omega + t.phase) * 0.55 * idle,
        t.roll0 + Math.sin(c * t.omega * 0.5 + t.phase * 0.7) * 0.1 * idle,
        'YXZ',
      );
    }
    this.renderer.render(this.scene, this.camera);
  }

  /** Text-line rectangles (in backdrop coordinates) of the page text that sits directly on the background. */
  private avoidZones(): { l: number; t: number; r: number; b: number }[] {
    const root = this.area ?? document;
    const out: { l: number; t: number; r: number; b: number }[] = [];
    root.querySelectorAll(AVOID_ATTR).forEach((el) => {
      const range = document.createRange();
      range.selectNodeContents(el);
      for (const r of Array.from(range.getClientRects())) {
        if (r.width < 1 || r.height < 1) continue;
        out.push({ l: r.left - this.left, t: r.top - this.top, r: r.right - this.left, b: r.bottom - this.top });
      }
    });
    return out;
  }

  /** 1 when the token is clear of every text line, easing to 0 as it closes in on one. */
  private fadeFor(t: Token, zones: { l: number; t: number; r: number; b: number }[]): number {
    const cx = t.nx * this.w;
    const cy = t.ny * this.h;
    // The sway turns the token, so use a generous half-extent (the shield is 0.8 as wide as it is tall).
    const hw = t.size * 0.5;
    const hh = t.size * 0.55;
    let k = 1;
    for (const z of zones) {
      const dx = Math.max(z.l - (cx + hw), cx - hw - z.r, 0);
      const dy = Math.max(z.t - (cy + hh), cy - hh - z.b, 0);
      const gap = Math.hypot(dx, dy);
      const x = Math.min(gap / AVOID_FADE_PX, 1);
      k = Math.min(k, x * x * (3 - 2 * x));
    }
    return k;
  }

  private teardown(): void {
    this.destroyed = true;
    this.stopLoop();
    document.removeEventListener('visibilitychange', this.onVisibility);
    window.removeEventListener('resize', this.onWindowResize);
    this.area?.removeEventListener('scroll', this.onScroll);
    this.motionQuery?.removeEventListener('change', this.onMotionPref);
    this.themeObserver?.disconnect();
    this.resizeObserver?.disconnect();
    this.textures.forEach((t) => t.dispose());
    this.textures = [];
    this.geometry?.dispose();
    for (const t of this.tokens) {
      t.cap.dispose();
      t.edge.dispose();
    }
    this.edgeMat?.dispose();
    this.graphiteMat?.dispose();
    this.crimsonMat?.dispose();
    (this.scene?.environment as ThreeNS.Texture | null)?.dispose();
    this.pmrem?.dispose();
    this.renderer?.dispose();
    this.renderer?.forceContextLoss();
    this.tokens = [];
    this.renderer = undefined;
    this.scene = undefined;
    this.ready = false;
  }
}
