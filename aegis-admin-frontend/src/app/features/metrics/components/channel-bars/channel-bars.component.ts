import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  NgZone,
  afterNextRender,
  effect,
  inject,
  input,
  viewChild,
} from '@angular/core';
import { animate, type AnimationPlaybackControls } from 'motion';
import type * as ThreeNS from 'three';
import type { MetricsChannel } from '../../../../core/models/metrics.models';

type Three = typeof ThreeNS;

/** One bar of the chart. `count` is the real value from the API; the chart never invents one. */
export interface ChannelBarDatum {
  channel: MetricsChannel;
  label: string;
  count: number;
}

/** Same icon language as dashboard.component.html (24x24 Feather-style strokes). */
const CHANNEL_ICON_PATHS: Record<MetricsChannel, string[]> = {
  EMAIL: [
    'M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z',
    'M22 6L12 13L2 6',
  ],
  SMS: ['M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z'],
  WHATSAPP: [
    'M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z',
  ],
  PUSH: ['M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9', 'M13.73 21a2 2 0 0 1-3.46 0'],
};

const CHANNELS: MetricsChannel[] = ['EMAIL', 'SMS', 'WHATSAPP', 'PUSH'];
const CHANNEL_NAMES: Record<MetricsChannel, string> = {
  EMAIL: 'EMAIL',
  SMS: 'SMS',
  WHATSAPP: 'WHATSAPP',
  PUSH: 'PUSH',
};

/** Same physical finish recipe as the Notification Detail shield (graphite faces, chrome bevels). */
interface Finish {
  face: number;
  edge: number;
  edgeRough: number;
  plateTop: string;
  plateBottom: string;
}
const FINISH_LIGHT: Finish = {
  face: 0x23252b,
  edge: 0xeef0f5,
  edgeRough: 0.14,
  plateTop: '#3c3f48',
  plateBottom: '#0f1013',
};
const FINISH_DARK: Finish = {
  face: 0x2e3038,
  edge: 0xd4d9e2,
  edgeRough: 0.16,
  plateTop: '#4a4e59',
  plateBottom: '#131418',
};

const ACCENT = 0xf4094c;

// World units. Bars are spaced along x on chrome pedestals; heights are normalised against the largest count.
const SPACING = 1.85;
const PLINTH_W = 1.55;
const PLINTH_THICK = 0.4;
const PLINTH_BEVEL = 0.05;
const PLINTH_H = PLINTH_THICK + PLINTH_BEVEL * 2;
const BAR_W = 1.06;
const BAR_MAX_H = 3;
/** A non-zero count never renders thinner than this nub, so a tiny real value stays visible. */
const BAR_MIN_H = 0.12;
const CAP_THICK = 0.05;
const CAP_BEVEL = 0.04;
const CAP_H = CAP_THICK + CAP_BEVEL * 2;
const CAP_W = BAR_W + 0.1;
/** Overall scene extent used to frame the camera: full-height chart incl. pedestals, and its width. */
const CHART_H = PLINTH_H + BAR_MAX_H + CAP_H + 0.3;
const CHART_W = SPACING * 3 + PLINTH_W + 0.5;

const SPRING = { type: 'spring', stiffness: 120, damping: 16, mass: 0.9 } as const;

/**
 * Live 3D bar chart of requests per channel, rendered as a fixed backdrop behind the Metrics page.
 * Same studio rendering recipe as the shield (RoomEnvironment-style softboxes, MeshPhysicalMaterial graphite
 * with chrome bevels). Bar height is the real count from the API, normalised to the largest value; with no
 * data (`null`) the bars rest at zero rather than showing made-up numbers. Owned by MetricsPage.
 */
@Component({
  selector: 'app-channel-bars',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<canvas #canvas></canvas>`,
  styleUrl: './channel-bars.component.scss',
  host: { 'aria-hidden': 'true' },
})
export class ChannelBarsComponent {
  /** Real per-channel counts, or null when live data is not available. */
  readonly data = input<ChannelBarDatum[] | null>(null);

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly zone = inject(NgZone);
  private readonly destroyRef = inject(DestroyRef);
  private readonly canvasRef = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');

  private THREE?: Three;
  private renderer?: ThreeNS.WebGLRenderer;
  private scene?: ThreeNS.Scene;
  private camera?: ThreeNS.PerspectiveCamera;
  private root?: ThreeNS.Group;
  private key?: ThreeNS.DirectionalLight;
  private pmrem?: ThreeNS.PMREMGenerator;
  private edgeMat?: ThreeNS.MeshPhysicalMaterial;
  private faceMat?: ThreeNS.MeshPhysicalMaterial;
  private plinthTopMat?: ThreeNS.MeshPhysicalMaterial;
  private plateMats: ThreeNS.MeshPhysicalMaterial[] = [];
  private plateTex: ThreeNS.CanvasTexture[] = [];
  private blobTex?: ThreeNS.CanvasTexture;
  private bodies: ThreeNS.Mesh[] = [];
  private caps: ThreeNS.Group[] = [];
  private inlays: ThreeNS.MeshPhysicalMaterial[] = [];
  private inlayRest?: ThreeNS.Color;
  private inlayLive?: ThreeNS.Color;

  private mainEl?: HTMLElement;
  private anchorEl?: HTMLElement | null;
  private resizeObserver?: ResizeObserver;
  private themeObserver?: MutationObserver;
  private motionQuery?: MediaQueryList;
  private running: AnimationPlaybackControls[] = [];

  private raf = 0;
  private lastTs = 0;
  private pending = 0;
  private reduced = false;
  private ready = false;
  private destroyed = false;

  /** Current rendered height per bar, and whether that bar carries a real, non-zero value. */
  private readonly heights = [0, 0, 0, 0];
  private readonly lit = [0, 0, 0, 0];

  // Pointer parallax: target and smoothed values relative to the chart.
  private tx = 0;
  private ty = 0;
  private px = 0;
  private py = 0;
  private cx = 0;
  private cy = 0;
  private hostLeft = 0;
  private hostTop = 0;
  private hostW = 1;
  private hostH = 1;
  private stageH = 1;

  constructor() {
    afterNextRender(() => {
      this.zone.runOutsideAngular(() => void this.init());
    });

    // New data (e.g. after Refresh): glide the bars to their new heights.
    effect(() => {
      const d = this.data();
      if (this.ready) this.applyData(d, true);
    });

    this.destroyRef.onDestroy(() => this.teardown());
  }

  private onVisibility = (): void => {
    if (document.hidden) this.stopLoop();
    else this.startLoop();
  };

  private onMotionPref = (e: MediaQueryListEvent): void => {
    this.reduced = e.matches;
    this.applyData(this.data(), false);
  };

  private onPointerMove = (e: PointerEvent): void => {
    if (this.reduced || e.pointerType === 'touch' || !this.renderer) return;
    this.tx = Math.min(Math.max((e.clientX - (this.hostLeft + this.cx)) / (this.hostW * 0.5), -1), 1);
    this.ty = Math.min(Math.max((e.clientY - (this.hostTop + this.cy)) / (this.hostH * 0.5), -1), 1);
    this.startLoop();
  };

  private onWindowResize = (): void => this.resize();

  /** The chart stays put while the page scrolls over it, but eases back so panels read cleanly on top. */
  private onScroll = (): void => {
    const main = this.mainEl;
    if (!main) return;
    const t = Math.min(Math.max(main.scrollTop / Math.max(this.stageH, 1), 0), 1);
    this.host.nativeElement.style.opacity = String(1 - 0.55 * t);
  };

  private async init(): Promise<void> {
    try {
      const [THREE, { toCreasedNormals }] = await Promise.all([
        import('three'),
        import('three/examples/jsm/utils/BufferGeometryUtils.js'),
        this.loadFonts(),
      ]);
      if (this.destroyed) return;
      this.THREE = THREE;
      const finish = this.finish();

      this.mainEl = this.host.nativeElement.closest('main') ?? document.body;
      this.anchorEl = this.host.nativeElement.parentElement?.querySelector<HTMLElement>('[data-chart-anchor]');

      const canvas = this.canvasRef().nativeElement;
      const renderer = new THREE.WebGLRenderer({
        canvas,
        antialias: true,
        alpha: true,
        powerPreference: 'high-performance',
      });
      renderer.setClearColor(0x000000, 0);
      renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
      renderer.outputColorSpace = THREE.SRGBColorSpace;
      renderer.toneMapping = THREE.NeutralToneMapping;
      renderer.toneMappingExposure = 1;
      renderer.shadowMap.enabled = true;
      renderer.shadowMap.type = THREE.VSMShadowMap;
      this.renderer = renderer;

      const scene = new THREE.Scene();
      this.scene = scene;

      // Studio image-based lighting: large soft boxes, so polished bevels pick up crisp streak highlights.
      this.pmrem = new THREE.PMREMGenerator(renderer);
      const studio = this.buildStudio(THREE);
      scene.environment = this.pmrem.fromScene(studio, 0.02).texture;
      studio.traverse((o) => {
        const m = o as ThreeNS.Mesh;
        m.geometry?.dispose();
        (m.material as ThreeNS.Material | undefined)?.dispose();
      });
      scene.environmentIntensity = 1;

      const camera = new THREE.PerspectiveCamera(22, 1, 0.1, 120);
      this.camera = camera;

      const key = new THREE.DirectionalLight(0xfff3e6, 1.4);
      key.position.set(-4, 8, 7);
      key.castShadow = true;
      key.shadow.mapSize.set(2048, 2048);
      key.shadow.camera.left = -7;
      key.shadow.camera.right = 7;
      key.shadow.camera.top = 6;
      key.shadow.camera.bottom = -6;
      key.shadow.camera.near = 2;
      key.shadow.camera.far = 30;
      key.shadow.radius = 6;
      key.shadow.blurSamples = 16;
      key.shadow.bias = -0.0008;
      key.shadow.normalBias = 0.02;
      scene.add(key);
      this.key = key;
      const rim = new THREE.DirectionalLight(0xdbe6ff, 1.2);
      rim.position.set(5, 3, -7);
      scene.add(rim);

      const root = new THREE.Group();
      this.root = root;
      scene.add(root);
      const chart = new THREE.Group();
      // Centre the full-height chart on the origin so framing maths stays simple.
      chart.position.y = -(PLINTH_H + BAR_MAX_H + CAP_H) / 2;
      root.add(chart);

      this.blobTex = this.makeBlobTexture();
      const blob = new THREE.Mesh(
        new THREE.PlaneGeometry(1, 1),
        new THREE.MeshBasicMaterial({ map: this.blobTex, transparent: true, depthWrite: false, toneMapped: false }),
      );
      blob.rotation.x = -Math.PI / 2;
      blob.position.y = -0.01;
      blob.scale.set(CHART_W + 1.4, 3.1, 1);
      chart.add(blob);

      this.edgeMat = new THREE.MeshPhysicalMaterial({
        color: finish.edge,
        metalness: 1,
        roughness: finish.edgeRough,
        clearcoat: 0.4,
        clearcoatRoughness: 0.1,
      });
      this.faceMat = new THREE.MeshPhysicalMaterial({
        color: finish.face,
        metalness: 0.35,
        roughness: 0.34,
        clearcoat: 1,
        clearcoatRoughness: 0.1,
      });
      this.plinthTopMat = this.faceMat.clone();

      // Accent inlay: muted graphite at rest, brand crimson once the channel has real traffic.
      this.inlayRest = new THREE.Color(0x3a3c44);
      this.inlayLive = new THREE.Color(0xb80535);
      this.buildBars(THREE, toCreasedNormals, chart);
      this.paintPlates();

      this.motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
      this.reduced = this.motionQuery.matches;
      this.motionQuery.addEventListener('change', this.onMotionPref);

      this.themeObserver = new MutationObserver(() => this.applyTheme());
      this.themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });

      this.resizeObserver = new ResizeObserver(() => this.resize());
      this.resizeObserver.observe(this.mainEl);
      if (this.anchorEl) this.resizeObserver.observe(this.anchorEl);
      this.mainEl.addEventListener('scroll', this.onScroll, { passive: true });
      window.addEventListener('resize', this.onWindowResize, { passive: true });
      window.addEventListener('pointermove', this.onPointerMove, { passive: true });
      document.addEventListener('visibilitychange', this.onVisibility);

      this.ready = true;
      this.resize();
      this.host.nativeElement.classList.add('is-live');
      // First paint grows the bars in from zero (instant under reduced motion).
      this.applyData(this.data(), true);
    } catch {
      // No WebGL (or context lost): the 2D stat cards stand on their own.
      this.host.nativeElement.classList.add('is-unsupported');
      this.teardown();
    }
  }

  private async loadFonts(): Promise<void> {
    if (!('fonts' in document)) return;
    const load = Promise.all([
      document.fonts.load('700 100px "Outfit Variable"'),
      document.fonts.load('600 40px "JetBrains Mono Variable"'),
    ]).then(() => undefined);
    const grace = new Promise<void>((resolve) => setTimeout(resolve, 1500));
    await Promise.race([load.catch(() => undefined), grace]);
  }

  private finish(): Finish {
    return document.documentElement.getAttribute('data-theme') === 'dark' ? FINISH_DARK : FINISH_LIGHT;
  }

  private applyTheme(): void {
    if (!this.ready) return;
    const f = this.finish();
    this.edgeMat?.color.setHex(f.edge);
    if (this.edgeMat) this.edgeMat.roughness = f.edgeRough;
    this.faceMat?.color.setHex(f.face);
    this.plinthTopMat?.color.setHex(f.face);
    this.paintPlates();
    this.renderFrame();
  }

  /** Studio environment: a dim dome with large emissive softboxes (HDR values > 1 make chrome read as lit). */
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

  /** Rounded-rectangle footprint in the XY plane, centred. */
  private footprint(THREE: Three, w: number, d: number, r: number): ThreeNS.Shape {
    const s = new THREE.Shape();
    const x = -w / 2;
    const y = -d / 2;
    s.moveTo(x + r, y);
    s.lineTo(x + w - r, y);
    s.absarc(x + w - r, y + r, r, -Math.PI / 2, 0, false);
    s.lineTo(x + w, y + d - r);
    s.absarc(x + w - r, y + d - r, r, 0, Math.PI / 2, false);
    s.lineTo(x + r, y + d);
    s.absarc(x + r, y + d - r, r, Math.PI / 2, Math.PI, false);
    s.lineTo(x, y + r);
    s.absarc(x + r, y + r, r, Math.PI, Math.PI * 1.5, false);
    return s;
  }

  /** A bevelled slab lying in XZ with its bottom at y = 0 (total height = thick + 2 * bevel). */
  private slab(
    THREE: Three,
    toCreasedNormals: typeof import('three/examples/jsm/utils/BufferGeometryUtils.js').toCreasedNormals,
    w: number,
    r: number,
    thick: number,
    bevel: number,
  ): ThreeNS.BufferGeometry {
    let geo: ThreeNS.BufferGeometry = new THREE.ExtrudeGeometry(this.footprint(THREE, w, w, r), {
      depth: thick,
      bevelEnabled: true,
      bevelThickness: bevel,
      bevelSize: bevel,
      bevelOffset: -bevel,
      bevelSegments: 8,
      steps: 1,
      curveSegments: 10,
    });
    geo.rotateX(-Math.PI / 2);
    geo.translate(0, bevel, 0);
    geo = toCreasedNormals(geo, 0.62);
    return geo;
  }

  private buildBars(
    THREE: Three,
    toCreasedNormals: typeof import('three/examples/jsm/utils/BufferGeometryUtils.js').toCreasedNormals,
    chart: ThreeNS.Group,
  ): void {
    const plinthGeo = this.slab(THREE, toCreasedNormals, PLINTH_W, 0.16, PLINTH_THICK, PLINTH_BEVEL);
    const capGeo = this.slab(THREE, toCreasedNormals, CAP_W, 0.13, CAP_THICK, CAP_BEVEL);
    // Unit-height body, walls only: scaling Y is exact, so animated heights never distort a bevel.
    const bodyGeo = new THREE.ExtrudeGeometry(this.footprint(THREE, BAR_W, BAR_W, 0.1), {
      depth: 1,
      bevelEnabled: false,
      curveSegments: 10,
    });
    bodyGeo.rotateX(-Math.PI / 2);
    const inlayGeo = new THREE.ShapeGeometry(this.footprint(THREE, BAR_W - 0.22, BAR_W - 0.22, 0.07), 10);
    inlayGeo.rotateX(-Math.PI / 2);
    const plateGeo = new THREE.PlaneGeometry(1.22, 0.29);

    for (let i = 0; i < CHANNELS.length; i++) {
      const slot = new THREE.Group();
      slot.position.x = (i - (CHANNELS.length - 1) / 2) * SPACING;
      chart.add(slot);

      const plinth = new THREE.Mesh(plinthGeo, [this.plinthTopMat!, this.edgeMat!]);
      plinth.castShadow = true;
      plinth.receiveShadow = true;
      slot.add(plinth);

      // Channel label plate, inset on the pedestal's front wall.
      const plateMat = new THREE.MeshPhysicalMaterial({
        roughness: 0.4,
        metalness: 0,
        clearcoat: 0.35,
        clearcoatRoughness: 0.3,
        envMapIntensity: 0.55,
      });
      this.plateMats.push(plateMat);
      const plate = new THREE.Mesh(plateGeo, plateMat);
      plate.position.set(0, PLINTH_H / 2, PLINTH_W / 2 + 0.004);
      slot.add(plate);

      const body = new THREE.Mesh(bodyGeo, this.faceMat!);
      body.position.y = PLINTH_H - 0.01;
      body.castShadow = true;
      body.receiveShadow = true;
      body.visible = false;
      slot.add(body);
      this.bodies.push(body);

      const cap = new THREE.Group();
      const slabMesh = new THREE.Mesh(capGeo, this.edgeMat!);
      slabMesh.castShadow = true;
      slabMesh.receiveShadow = true;
      cap.add(slabMesh);
      // Crimson brand-accent inlay on the top face. It glows only for a channel that has real traffic.
      const inlayMat = new THREE.MeshPhysicalMaterial({
        color: ACCENT,
        emissive: ACCENT,
        emissiveIntensity: 0,
        roughness: 0.5,
        metalness: 0,
        clearcoat: 0.35,
        clearcoatRoughness: 0.25,
        envMapIntensity: 0.12,
      });
      this.inlays.push(inlayMat);
      const inlay = new THREE.Mesh(inlayGeo, inlayMat);
      inlay.position.y = CAP_H + 0.0015;
      cap.add(inlay);
      slot.add(cap);
      this.caps.push(cap);
    }
  }

  private makeBlobTexture(): ThreeNS.CanvasTexture {
    const THREE = this.THREE!;
    const c = document.createElement('canvas');
    c.width = c.height = 256;
    const ctx = c.getContext('2d')!;
    const g = ctx.createRadialGradient(128, 128, 0, 128, 128, 128);
    g.addColorStop(0, 'rgba(20,20,26,0.34)');
    g.addColorStop(0.5, 'rgba(20,20,26,0.12)');
    g.addColorStop(1, 'rgba(20,20,26,0)');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, 256, 256);
    const tex = new THREE.CanvasTexture(c);
    tex.colorSpace = THREE.SRGBColorSpace;
    return tex;
  }

  /** Prints each pedestal's label plate: graphite glass, crimson tick, channel icon and name. */
  private paintPlates(): void {
    const THREE = this.THREE;
    if (!THREE || !this.renderer || !this.plateMats.length) return;
    const aniso = this.renderer.capabilities.getMaxAnisotropy();
    const f = this.finish();
    const display = '"Outfit Variable", Outfit, "Inter Variable", system-ui, sans-serif';

    this.plateTex.forEach((t) => t.dispose());
    this.plateTex = CHANNELS.map((channel) => {
      const c = document.createElement('canvas');
      c.width = 1220;
      c.height = 290;
      const ctx = c.getContext('2d')!;
      const g = ctx.createLinearGradient(0, 0, 1220, 290);
      g.addColorStop(0, f.plateTop);
      g.addColorStop(1, f.plateBottom);
      ctx.fillStyle = g;
      ctx.fillRect(0, 0, 1220, 290);
      ctx.fillStyle = '#f4094c';
      ctx.fillRect(0, 0, 14, 290);
      const s = 7.6;
      ctx.save();
      ctx.translate(96, 145 - 12 * s);
      ctx.scale(s, s);
      ctx.strokeStyle = '#f6f5f2';
      ctx.lineWidth = 1.5;
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';
      for (const d of CHANNEL_ICON_PATHS[channel]) ctx.stroke(new Path2D(d));
      ctx.restore();
      ctx.fillStyle = 'rgba(244,243,240,0.92)';
      ctx.textBaseline = 'middle';
      ctx.textAlign = 'left';
      ctx.font = `700 92px ${display}`;
      (ctx as CanvasRenderingContext2D & { letterSpacing: string }).letterSpacing = '6px';
      ctx.fillText(CHANNEL_NAMES[channel], 330, 152);
      const tex = new THREE.CanvasTexture(c);
      tex.colorSpace = THREE.SRGBColorSpace;
      tex.anisotropy = aniso;
      tex.minFilter = THREE.LinearMipmapLinearFilter;
      tex.needsUpdate = true;
      return tex;
    });
    this.plateMats.forEach((m, i) => {
      m.map = this.plateTex[i];
      m.needsUpdate = true;
    });
  }

  /**
   * Fixed backdrop framing: the canvas covers the content area (the shell's <main>); the chart is placed where
   * the page's [data-chart-anchor] spacer sits at scroll 0 and then stays put while the page scrolls over it.
   */
  private resize(): void {
    if (!this.renderer || !this.camera || !this.root || !this.mainEl) return;
    const main = this.mainEl;
    const mr = main.getBoundingClientRect();
    const w = Math.max(main.clientWidth, 1);
    const h = Math.max(main.clientHeight, 1);
    const hostStyle = this.host.nativeElement.style;
    hostStyle.left = `${mr.left}px`;
    hostStyle.top = `${mr.top}px`;
    hostStyle.width = `${w}px`;
    hostStyle.height = `${h}px`;
    this.hostLeft = mr.left;
    this.hostTop = mr.top;
    this.hostW = w;
    this.hostH = h;

    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2, Math.sqrt(5_500_000 / (w * h))));
    this.renderer.setSize(w, h, false);
    const aspect = w / h;
    this.camera.aspect = aspect;

    const anchor = this.anchorEl;
    const ar = anchor?.getBoundingClientRect();
    const aw = ar?.width ?? w * 0.8;
    const ah = ar?.height ?? h * 0.4;
    this.stageH = ah;
    this.cx = ar ? ar.left - mr.left + ar.width / 2 : w / 2;
    this.cy = ar ? ar.top - mr.top + main.scrollTop + ar.height / 2 : h * 0.3;

    const pxPerWorld = Math.min((ah * 0.96) / CHART_H, (aw * 0.96) / CHART_W);
    const visH = h / pxPerWorld;
    const dist = visH / (2 * Math.tan((this.camera.fov * Math.PI) / 360));
    const elev = 0.27;
    this.camera.position.set(0, Math.sin(elev) * dist, Math.cos(elev) * dist);
    this.camera.lookAt(0, 0, 0);
    this.camera.updateProjectionMatrix();
    this.root.position.set((this.cx / w - 0.5) * visH * aspect, (0.5 - this.cy / h) * visH, 0);
    this.renderFrame();
  }

  /**
   * Maps real counts to target heights (largest count = full height) and moves the bars there.
   * `null` data means no live values, so every bar rests at zero.
   */
  private applyData(data: ChannelBarDatum[] | null, animateIt: boolean): void {
    if (!this.ready && animateIt) return;
    this.running.forEach((c) => c.stop());
    this.running = [];
    this.pending = 0;
    const counts = CHANNELS.map((ch) => Math.max(data?.find((d) => d.channel === ch)?.count ?? 0, 0));
    const max = Math.max(...counts);
    const targets = counts.map((n) => (n > 0 && max > 0 ? Math.max((n / max) * BAR_MAX_H, BAR_MIN_H) : 0));

    targets.forEach((target, i) => {
      this.lit[i] = counts[i] > 0 ? 1 : 0;
      if (!animateIt || this.reduced || Math.abs(this.heights[i] - target) < 1e-4) {
        this.heights[i] = target;
        return;
      }
      this.pending++;
      this.running.push(
        animate(this.heights[i], target, {
          ...SPRING,
          delay: i * 0.07,
          onUpdate: (v: number) => {
            this.heights[i] = Math.max(v, 0);
          },
          onComplete: () => {
            this.heights[i] = target;
            this.pending = Math.max(this.pending - 1, 0);
          },
        }),
      );
    });
    this.renderFrame();
    if (this.pending > 0) this.startLoop();
  }

  private startLoop(): void {
    if (this.raf || !this.ready || document.hidden || (this.reduced && this.pending === 0)) return;
    this.lastTs = performance.now();
    const tick = (ts: number): void => {
      this.raf = 0;
      const dt = Math.min((ts - this.lastTs) / 1000, 0.1);
      this.lastTs = ts;
      const k = 1 - Math.exp(-dt * 6);
      this.px += (this.tx - this.px) * k;
      this.py += (this.ty - this.py) * k;
      this.renderFrame();
      const settling = Math.abs(this.tx - this.px) > 0.002 || Math.abs(this.ty - this.py) > 0.002;
      if (!document.hidden && !this.destroyed && (this.pending > 0 || settling)) {
        this.raf = requestAnimationFrame(tick);
      }
    };
    this.raf = requestAnimationFrame(tick);
  }

  private stopLoop(): void {
    if (this.raf) cancelAnimationFrame(this.raf);
    this.raf = 0;
  }

  private renderFrame(): void {
    if (!this.renderer || !this.scene || !this.camera || !this.root) return;
    const idle = this.reduced ? 0 : 1;
    this.root.rotation.set(this.py * 0.08 * idle, -0.2 + this.px * 0.32 * idle, 0, 'YXZ');
    for (let i = 0; i < this.bodies.length; i++) {
      const h = this.heights[i];
      const body = this.bodies[i];
      body.visible = h > 0.003;
      body.scale.y = Math.max(h, 1e-4);
      this.caps[i].position.y = PLINTH_H - 0.01 + h;
      // The accent inlay glows with the bar as it grows, and stays dark for a channel with no traffic.
      const glow = this.lit[i] * Math.min(h / 0.35, 1);
      this.inlays[i].emissiveIntensity = glow * 0.35;
      this.inlays[i].color.copy(this.inlayRest!).lerp(this.inlayLive!, glow);
    }
    if (this.key) this.key.position.set(-4 + this.px * 4 * idle, 8 - this.py * 2 * idle, 7);
    this.renderer.render(this.scene, this.camera);
  }

  private teardown(): void {
    this.destroyed = true;
    this.stopLoop();
    this.running.forEach((c) => c.stop());
    this.running = [];
    document.removeEventListener('visibilitychange', this.onVisibility);
    window.removeEventListener('pointermove', this.onPointerMove);
    window.removeEventListener('resize', this.onWindowResize);
    this.mainEl?.removeEventListener('scroll', this.onScroll);
    this.motionQuery?.removeEventListener('change', this.onMotionPref);
    this.themeObserver?.disconnect();
    this.resizeObserver?.disconnect();
    this.plateTex.forEach((t) => t.dispose());
    this.blobTex?.dispose();
    this.scene?.traverse((o) => {
      const mesh = o as ThreeNS.Mesh;
      mesh.geometry?.dispose();
      const m = mesh.material;
      if (Array.isArray(m)) m.forEach((x) => x.dispose());
      else m?.dispose();
    });
    this.faceMat?.dispose();
    this.plinthTopMat?.dispose();
    this.edgeMat?.dispose();
    (this.scene?.environment as ThreeNS.Texture | null)?.dispose();
    this.pmrem?.dispose();
    this.renderer?.dispose();
    this.renderer?.forceContextLoss();
    this.renderer = undefined;
    this.scene = undefined;
    this.ready = false;
  }
}
