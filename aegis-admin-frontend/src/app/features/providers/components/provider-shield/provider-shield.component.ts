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
import { animate, motionValue, type AnimationPlaybackControls } from 'motion';
import type * as ThreeNS from 'three';
import type { Channel, CircuitBreakerState } from '../../../../core/models/dashboard.models';

type Three = typeof ThreeNS;

/** Health of one channel, using the same four tones the provider cards use (`circuitTone`). */
export type ShieldTone = 'healthy' | 'degraded' | 'open' | 'neutral';
export type ChannelTones = Record<Channel, ShieldTone>;

export const NEUTRAL_TONES: ChannelTones = {
  EMAIL: 'neutral',
  SMS: 'neutral',
  WHATSAPP: 'neutral',
  PUSH: 'neutral',
};

/** Same mapping as ProviderCardComponent.circuitTone. */
export function toneOfCircuit(state: CircuitBreakerState): ShieldTone {
  switch (state) {
    case 'CLOSED':
      return 'healthy';
    case 'HALF_OPEN':
      return 'degraded';
    case 'OPEN':
    case 'FORCED_OPEN':
      return 'open';
    default:
      return 'neutral';
  }
}

const SEVERITY: Record<ShieldTone, number> = { neutral: 0, healthy: 1, degraded: 2, open: 3 };

/** A channel has a primary and a fallback provider: it takes the worst tone of its providers. */
export function worstTone(tones: readonly ShieldTone[]): ShieldTone {
  return tones.reduce<ShieldTone>((worst, t) => (SEVERITY[t] > SEVERITY[worst] ? t : worst), 'neutral');
}

/**
 * The AegisNotify brand mark, copied verbatim from the sidebar logo (sidebar.component.html) and used by the
 * Notification Detail shield. It is parsed by three's SVGLoader and extruded, so the 3D object is this silhouette.
 */
const SHIELD_PATH = 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z';
const SHIELD_SVG = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="${SHIELD_PATH}"/></svg>`;

/** SVG units (viewBox 24) -> world units. The shield is 16 x 20 units, so 3.2 x 4.0 in the scene. */
const S = 0.2;
const SHIELD_CY = 12;
/**
 * The shield is symmetric about x = 12, so the cleanest split is the vertical axis plus one horizontal cut.
 * y = 10.4 balances the area: the top half carries the wide shoulders, the bottom half the tapering point.
 */
const CUT_Y = 10.4;
const CUT_WORLD_Y = (SHIELD_CY - CUT_Y) * S;

/** One quadrant per channel. sx/sy are the outward direction of the quadrant from the shield centre. */
const QUADS: readonly { channel: Channel; label: string; sx: -1 | 1; sy: -1 | 1 }[] = [
  { channel: 'EMAIL', label: 'EMAIL', sx: -1, sy: 1 },
  { channel: 'SMS', label: 'SMS', sx: 1, sy: 1 },
  { channel: 'WHATSAPP', label: 'WHATSAPP', sx: -1, sy: -1 },
  { channel: 'PUSH', label: 'PUSH', sx: 1, sy: -1 },
];
const N = QUADS.length;

const DEPTH = 0.2;
const BEVEL_T = 0.06;
const BEVEL_S = 0.05;

/** Same icon language as dashboard.component.html (24x24 Feather-style strokes). */
const CHANNEL_ICON_PATHS: Record<Channel, string[]> = {
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

/** Channel accent ring printed around each icon (bright variants of the dashboard's channel hues). */
const CHANNEL_RING: Record<Channel, string> = {
  EMAIL: '#ff3d70',
  SMS: '#f062a8',
  PUSH: '#f062a8',
  WHATSAPP: '#2dd4a0',
};

/** Physical finish of the object; printed ink on the faces is part of the finish, not the page tokens. */
interface Finish {
  faceTop: string;
  faceBottom: string;
  edge: number;
  edgeRough: number;
  label: string;
}
const FINISH_LIGHT: Finish = {
  faceTop: '#34363e',
  faceBottom: '#121316',
  edge: 0xeef0f5,
  edgeRough: 0.14,
  label: 'rgba(244,243,240,0.7)',
};
const FINISH_DARK: Finish = {
  faceTop: '#444852',
  faceBottom: '#1b1c21',
  edge: 0xd4d9e2,
  edgeRough: 0.16,
  label: 'rgba(244,243,240,0.7)',
};

/** Tone -> semantic colour token, the exact ones provider-card.component.scss uses for abnormal circuits. */
function toneVar(tone: ShieldTone): string | null {
  switch (tone) {
    case 'open':
      return '--status-offline';
    case 'degraded':
      return '--status-warning';
    default:
      return null; // healthy / neutral: calm chrome, no tint
  }
}

/**
 * A pose is a flat Float32Array:
 *   [0..2]  whole shield: pitch, yaw, roll
 *   [3..]   per quadrant (7 each): dx, dy, dz, pitch, yaw, roll, glow
 * Quadrant transforms pivot around the quadrant's own centre.
 */
const Q_AT = 3;
const Q_STRIDE = 7;
const POSE_LEN = Q_AT + N * Q_STRIDE;

/** Healthy / unknown: the quadrant sits home, just parted from its neighbours so the chrome seams read. */
function quadPose(q: (typeof QUADS)[number], tone: ShieldTone): number[] {
  const { sx, sy } = q;
  switch (tone) {
    case 'degraded':
      // Half-open: the quadrant lifts toward the viewer and drifts outward, still attached.
      return [sx * 0.12, sy * 0.1, 0.42, sy * -0.06, sx * -0.12, sx * sy * 0.05, 1];
    case 'open':
      // Open: the quadrant breaks away askew, like the failed dispatch band on the detail shield.
      return [sx * 0.24, sy * 0.34, 0.3, sy * -0.16, sx * 0.22, sx * sy * -0.3, 1];
    default:
      return [sx * 0.035, sy * 0.035, 0, 0, 0, 0, 0];
  }
}

function buildPose(tones: ChannelTones): Float32Array {
  const out = new Float32Array(POSE_LEN);
  out.set([0.05, -0.24, 0], 0);
  QUADS.forEach((q, i) => out.set(quadPose(q, tones[q.channel]), Q_AT + i * Q_STRIDE));
  return out;
}

/** Narrowest gutter (px) worth centring the shield in, and the shield's world width with exploded quadrants. */
const GUTTER_MIN = 240;
const SHIELD_SPAN = 3.7;

const SPRING = { type: 'spring', stiffness: 110, damping: 15, mass: 0.9 } as const;
const GLOW = 4.6;
/** The bevel catches a fraction of the status tint, so the chrome edges also read through narrow gaps. */
const EDGE_GLOW = 0.55;

/**
 * Providers backdrop: the AegisNotify shield cut into four quadrants, one per channel, each tinting toward that
 * channel's real circuit-breaker health. The host is `position: fixed` over the page's scroll container, so the
 * object is a persistent background; the page keeps every real element opaque on top of it.
 */
@Component({
  selector: 'app-provider-shield',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<canvas #canvas aria-hidden="true"></canvas>`,
  styleUrl: './provider-shield.component.scss',
})
export class ProviderShieldComponent {
  readonly tones = input.required<ChannelTones>();

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly zone = inject(NgZone);
  private readonly destroyRef = inject(DestroyRef);
  private readonly canvasRef = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');

  private readonly t = motionValue(1);
  private readonly colorMix = motionValue(1);
  private running: AnimationPlaybackControls[] = [];
  private from = buildPose(NEUTRAL_TONES);
  private to = this.from;
  private readonly cur = this.from.slice();

  private THREE?: Three;
  private renderer?: ThreeNS.WebGLRenderer;
  private scene?: ThreeNS.Scene;
  private camera?: ThreeNS.PerspectiveCamera;
  private root?: ThreeNS.Group;
  private shield?: ThreeNS.Group;
  private pivots: ThreeNS.Group[] = [];
  private centres: { x: number; y: number }[] = [];
  private sizes: { w: number; h: number; top: number; bottom: number }[] = [];
  private capMats: ThreeNS.MeshPhysicalMaterial[] = [];
  private edgeMats: ThreeNS.MeshPhysicalMaterial[] = [];
  private capTex: ThreeNS.CanvasTexture[] = [];
  private key?: ThreeNS.DirectionalLight;
  private pmrem?: ThreeNS.PMREMGenerator;
  private themeObserver?: MutationObserver;
  private resizeObserver?: ResizeObserver;
  private motionQuery?: MediaQueryList;
  private scroller: HTMLElement | null = null;
  private raf = 0;
  private reduced = false;
  private destroyed = false;
  private ready = false;
  private fromColors: ThreeNS.Color[] = [];
  private toColors: ThreeNS.Color[] = [];
  private colors: ThreeNS.Color[] = [];
  private xFrac = 0.5;
  private tx = 0;
  private ty = 0;
  private px = 0;
  private py = 0;
  private clock = 0;
  private lastTs = 0;

  constructor() {
    afterNextRender(() => {
      this.zone.runOutsideAngular(() => void this.init());
    });

    // Re-pose and re-tint whenever the real channel health changes.
    effect(() => {
      const tones = this.tones();
      if (this.ready) this.goTo(tones, !this.reduced);
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
      this.tx = this.ty = this.px = this.py = 0;
    }
    this.goTo(this.tones(), false);
    this.startLoop();
  };

  private onPointerMove = (e: PointerEvent): void => {
    if (this.reduced || e.pointerType === 'touch' || !this.renderer) return;
    const r = this.host.nativeElement.getBoundingClientRect();
    const cx = r.left + r.width * this.xFrac;
    const cy = r.top + r.height * 0.5;
    this.tx = Math.min(Math.max((e.clientX - cx) / (r.width * 0.5), -1), 1);
    this.ty = Math.min(Math.max((e.clientY - cy) / (r.height * 0.5), -1), 1);
  };

  private onPointerLeave = (): void => {
    this.tx = 0;
    this.ty = 0;
  };

  private onWindowResize = (): void => this.resize();

  private async init(): Promise<void> {
    try {
      const [THREE, { SVGLoader }, { toCreasedNormals }] = await Promise.all([
        import('three'),
        import('three/examples/jsm/loaders/SVGLoader.js'),
        import('three/examples/jsm/utils/BufferGeometryUtils.js'),
        // Printed copy is drawn to canvases, so the display and mono faces must be ready first.
        this.loadFonts(),
      ]);
      if (this.destroyed) return;
      this.THREE = THREE;
      const finish = this.finish();

      const canvas = this.canvasRef().nativeElement;
      // Transparent canvas: the object sits straight on the page, so --bg-main (light or dark) is the backdrop.
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

      const camera = new THREE.PerspectiveCamera(22, 1, 0.1, 80);
      camera.position.set(0, 0.2, 12);
      camera.lookAt(0, 0, 0);
      this.camera = camera;

      // Key light casts the quadrant-on-quadrant shadows and follows the pointer, so highlights sweep the bevels.
      const key = new THREE.DirectionalLight(0xfff3e6, 1.4);
      key.position.set(-3, 6, 6);
      key.castShadow = true;
      key.shadow.mapSize.set(1024, 1024);
      key.shadow.camera.left = -3.5;
      key.shadow.camera.right = 3.5;
      key.shadow.camera.top = 3.5;
      key.shadow.camera.bottom = -3.5;
      key.shadow.camera.near = 2;
      key.shadow.camera.far = 20;
      key.shadow.radius = 6;
      key.shadow.blurSamples = 16;
      key.shadow.bias = -0.0008;
      key.shadow.normalBias = 0.02;
      scene.add(key);
      this.key = key;
      const rim = new THREE.DirectionalLight(0xdbe6ff, 1.2);
      rim.position.set(4, 2, -6);
      scene.add(rim);

      const root = new THREE.Group();
      this.root = root;
      scene.add(root);
      const shield = new THREE.Group();
      this.shield = shield;
      root.add(shield);

      // Quadrants start on the neutral status colour, so a first tint animates from calm rather than from white.
      this.colors = QUADS.map(() => this.cssColor('--status-neutral'));
      this.fromColors = QUADS.map(() => new THREE.Color());
      this.toColors = QUADS.map(() => new THREE.Color());

      this.buildQuadrants(THREE, SVGLoader, toCreasedNormals, finish);

      this.paintTextures();
      const first = buildPose(this.tones());
      this.cur.set(first);
      this.from = first.slice();
      this.to = first;
      this.applyColors(this.tones(), false);

      this.t.on('change', (v) => this.mixPose(v));
      this.colorMix.on('change', (v) => this.mixColor(v));

      this.motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
      this.reduced = this.motionQuery.matches;
      this.motionQuery.addEventListener('change', this.onMotionPref);

      // Follow the theme toggle: re-finish the metal and reprint the faces.
      this.themeObserver = new MutationObserver(() => this.applyTheme());
      this.themeObserver.observe(document.documentElement, {
        attributes: true,
        attributeFilter: ['data-theme'],
      });

      // The backdrop is fixed over the app's scroll container (not the whole viewport), so it never covers the
      // sidebar or topbar: follow that container's box.
      this.scroller = this.host.nativeElement.closest<HTMLElement>('.content-area');
      this.resizeObserver = new ResizeObserver(() => this.resize());
      this.resizeObserver.observe(this.scroller ?? document.documentElement);
      window.addEventListener('resize', this.onWindowResize, { passive: true });
      this.resize();

      // The page copy sits above the canvas, so the pointer is tracked on the window.
      window.addEventListener('pointermove', this.onPointerMove, { passive: true });
      document.documentElement.addEventListener('mouseleave', this.onPointerLeave, { passive: true });
      document.addEventListener('visibilitychange', this.onVisibility);

      this.ready = true;
      this.host.nativeElement.classList.add('is-live');
      this.renderFrame();
      this.startLoop();
    } catch {
      // No WebGL (or context lost): the page stands on its own.
      this.host.nativeElement.classList.add('is-unsupported');
      this.teardown();
    }
  }

  /** Resolves once the faces used in printed copy are loaded (or after a short grace period). */
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
    if (!this.ready || !this.THREE) return;
    const f = this.finish();
    for (const m of this.edgeMats) {
      m.color.setHex(f.edge);
      m.roughness = f.edgeRough;
    }
    this.paintTextures();
    this.applyColors(this.tones(), false);
    this.renderFrame();
  }

  /**
   * Studio environment for reflections: a dim dome with large emissive softboxes (top, two side strips and
   * a broad front panel). HDR values > 1 are what make the chrome bevels and clearcoat faces read as lit.
   */
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
    box(16, 9, 0, 13, 3, 6.5); // overhead softbox
    box(2.6, 15, -12, 1, 5, 9, 1.04); // tall left strip
    box(2.6, 15, 12, 1, 3, 6, 0.9); // tall right strip (cooler, dimmer)
    box(20, 7, 0, 2.5, 17, 2.6); // broad panel behind the camera: gives flat faces a gradient sheen
    box(18, 4, 0, -9, 8, 1.1); // low bounce
    return env;
  }

  /**
   * Builds the shield from the real brand path: SVGLoader parses SHIELD_PATH into a ShapePath, the shape is
   * sampled along its Bezier/line segments, then clipped into four quadrants (x = 0 axis, y = CUT_WORLD_Y cut)
   * and each quadrant is extruded with a rounded bevel (bevelOffset keeps the outer wall exactly on the path).
   */
  private buildQuadrants(
    THREE: Three,
    SVGLoader: typeof import('three/examples/jsm/loaders/SVGLoader.js').SVGLoader,
    toCreasedNormals: typeof import('three/examples/jsm/utils/BufferGeometryUtils.js').toCreasedNormals,
    finish: Finish,
  ): void {
    const parsed = new SVGLoader().parse(SHIELD_SVG);
    const shapes = parsed.paths[0].toShapes();
    const raw = shapes[0].getPoints(40);
    const outline: ThreeNS.Vector2[] = [];
    for (const p of raw) {
      // SVG y runs down; world y runs up. Centre the viewBox on the origin.
      const v = new THREE.Vector2((p.x - 12) * S, (SHIELD_CY - p.y) * S);
      const prev = outline[outline.length - 1];
      if (!prev || prev.distanceTo(v) > 1e-5) outline.push(v);
    }
    if (outline.length > 1 && outline[0].distanceTo(outline[outline.length - 1]) < 1e-5) outline.pop();

    // Sutherland-Hodgman against one half-plane: `axis` is the coordinate tested against `at`, `keepAbove` the side kept.
    const clip = (poly: ThreeNS.Vector2[], axis: 'x' | 'y', at: number, keepAbove: boolean): ThreeNS.Vector2[] => {
      const other = axis === 'x' ? 'y' : 'x';
      const inside = (p: ThreeNS.Vector2): boolean => (keepAbove ? p[axis] >= at - 1e-9 : p[axis] <= at + 1e-9);
      const out: ThreeNS.Vector2[] = [];
      for (let i = 0; i < poly.length; i++) {
        const a = poly[i];
        const b = poly[(i + 1) % poly.length];
        const ia = inside(a);
        const ib = inside(b);
        if (ia) out.push(a);
        if (ia !== ib) {
          const k = (at - a[axis]) / (b[axis] - a[axis]);
          const v = new THREE.Vector2();
          v[axis] = at;
          v[other] = a[other] + (b[other] - a[other]) * k;
          out.push(v);
        }
      }
      return out;
    };

    QUADS.forEach((q, i) => {
      let poly = clip(outline, 'x', 0, q.sx > 0);
      poly = clip(poly, 'y', CUT_WORLD_Y, q.sy > 0);
      const cleaned: ThreeNS.Vector2[] = [];
      for (const p of poly) {
        const prev = cleaned[cleaned.length - 1];
        if (!prev || prev.distanceTo(p) > 1e-5) cleaned.push(p);
      }
      if (cleaned.length > 1 && cleaned[0].distanceTo(cleaned[cleaned.length - 1]) < 1e-5) cleaned.pop();

      let minX = Infinity;
      let maxX = -Infinity;
      let minY = Infinity;
      let maxY = -Infinity;
      for (const p of cleaned) {
        minX = Math.min(minX, p.x);
        maxX = Math.max(maxX, p.x);
        minY = Math.min(minY, p.y);
        maxY = Math.max(maxY, p.y);
      }
      const w = maxX - minX;
      const h = maxY - minY;
      const cx = (minX + maxX) / 2;
      const cy = (minY + maxY) / 2;
      this.sizes.push({ w, h, top: maxY, bottom: minY });
      this.centres.push({ x: cx, y: cy });

      // Front/back caps map onto the quadrant's own bounding box, so each quadrant gets its own printed face.
      const uv = (v: number[], a: number, b: number, c: number): ThreeNS.Vector2[] =>
        [a, b, c].map((k) => new THREE.Vector2((v[k * 3] - minX) / w, (v[k * 3 + 1] - minY) / h));
      let geo: ThreeNS.BufferGeometry = new THREE.ExtrudeGeometry(new THREE.Shape(cleaned), {
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
      geo.translate(-cx, -cy, -DEPTH / 2);
      geo = toCreasedNormals(geo, 0.62);

      const cap = new THREE.MeshPhysicalMaterial({
        color: 0xffffff,
        metalness: 0.35,
        roughness: 0.34,
        clearcoat: 1,
        clearcoatRoughness: 0.1,
        emissive: 0x000000,
        emissiveIntensity: 0,
      });
      this.capMats.push(cap);
      // One chrome edge material per quadrant, so each bevel can carry its own share of the status tint.
      const edge = new THREE.MeshPhysicalMaterial({
        color: finish.edge,
        metalness: 1,
        roughness: finish.edgeRough,
        clearcoat: 0.4,
        clearcoatRoughness: 0.1,
        emissive: 0x000000,
        emissiveIntensity: 0,
      });
      this.edgeMats.push(edge);
      // Group 0 = front/back caps, group 1 = side walls + bevel (ExtrudeGeometry convention).
      const mesh = new THREE.Mesh(geo, [cap, edge]);
      mesh.castShadow = true;
      mesh.receiveShadow = true;
      const pivot = new THREE.Group();
      pivot.position.set(cx, cy, 0);
      pivot.add(mesh);
      this.shield!.add(pivot);
      this.pivots.push(pivot);
    });
  }

  private resize(): void {
    if (!this.renderer || !this.camera || !this.root) return;
    const hostEl = this.host.nativeElement;
    // Fixed to the scroll container's visible box (minus its scrollbar), or to the viewport without one.
    const box = this.scroller?.getBoundingClientRect();
    const left = box?.left ?? 0;
    const top = box?.top ?? 0;
    const w = Math.max(Math.round(this.scroller?.clientWidth ?? window.innerWidth), 1);
    const h = Math.max(Math.round(this.scroller?.clientHeight ?? window.innerHeight), 1);
    hostEl.style.left = `${left}px`;
    hostEl.style.top = `${top}px`;
    hostEl.style.width = `${w}px`;
    hostEl.style.height = `${h}px`;

    // A full-area backdrop on a hi-dpi screen would be millions of extra pixels: cap the backing store.
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.75, Math.sqrt(4_500_000 / (w * h))));
    this.renderer.setSize(w, h, false);
    const aspect = w / h;
    this.camera.aspect = aspect;
    // CSS owns the composition: --shield-x is where (0..1) across the backdrop the shield's centre sits.
    const raw = parseFloat(getComputedStyle(hostEl).getPropertyValue('--shield-x'));
    this.xFrac = Number.isFinite(raw) ? raw : 0.5;
    // The shield (4 world units tall) is drawn ~85% of the backdrop height, narrowed on portrait screens to stay whole.
    let visH = Math.max(4.7, 3.7 / aspect);
    // If the page leaves a free gutter beside its content (the nearest [data-shield-anchor]'s right edge to the
    // backdrop's right edge), the shield is centred in that whitespace and scaled to fit it.
    const anchor = hostEl.closest<HTMLElement>('[data-shield-anchor]');
    // Free space starts where the content ends: the anchor's right edge minus its own right padding.
    const anchorEdge = anchor ? anchor.getBoundingClientRect().right - (parseFloat(getComputedStyle(anchor).paddingRight) || 0) : 0;
    const free = anchor ? left + w - anchorEdge : 0;
    if (anchor && free >= GUTTER_MIN) {
      visH = Math.max(visH, (SHIELD_SPAN * h) / (free * 1.02));
      // Centred in the gutter, but never past the backdrop edge: any overlap tucks behind the (opaque) cards.
      const spanPx = (SHIELD_SPAN * h) / visH;
      this.xFrac = Math.min((anchorEdge - left + free / 2) / w, (w - 28 - spanPx / 2) / w);
    }
    const dist = visH / (2 * Math.tan((this.camera.fov * Math.PI) / 360));
    const visW = visH * aspect;
    this.camera.position.set(0, 0.1, dist);
    this.camera.lookAt(0, 0.02, 0);
    this.camera.updateProjectionMatrix();
    this.root.position.x = (this.xFrac - 0.5) * visW;
    this.renderFrame();
  }

  /** Re-pose the quadrants and re-tint them for the channels' current health. */
  private goTo(tones: ChannelTones, animated: boolean): void {
    this.running.forEach((c) => c.stop());
    this.running = [];
    const next = buildPose(tones);
    this.applyColors(tones, animated);
    this.from = this.cur.slice();
    this.to = next;
    if (!animated) {
      this.cur.set(next);
      this.t.set(1);
      this.colorMix.set(1);
      this.renderFrame();
      return;
    }
    this.t.set(0);
    this.running.push(animate(this.t, 1, SPRING));
    this.startLoop();
  }

  private mixPose(v: number): void {
    for (let i = 0; i < POSE_LEN; i++) this.cur[i] = this.from[i] + (this.to[i] - this.from[i]) * v;
  }

  private cssColor(varName: string): ThreeNS.Color {
    const THREE = this.THREE!;
    const raw = getComputedStyle(this.host.nativeElement).getPropertyValue(varName).trim() || '#94a3b8';
    return new THREE.Color(raw);
  }

  private applyColors(tones: ChannelTones, animated: boolean): void {
    if (!this.THREE || !this.colors.length) return;
    QUADS.forEach((q, i) => {
      this.fromColors[i].copy(this.colors[i]);
      const token = toneVar(tones[q.channel]);
      // Going calm keeps the last tint while the glow fades out, instead of flashing through grey.
      if (token) this.toColors[i].copy(this.cssColor(token));
      else if (!animated) this.toColors[i].copy(this.colors[i]);
      else this.toColors[i].copy(this.fromColors[i]);
      if (!animated && token) this.colors[i].copy(this.toColors[i]);
    });
    if (!animated) return;
    this.colorMix.set(0);
    this.running.push(animate(this.colorMix, 1, { duration: 0.45, ease: 'easeOut' }));
  }

  private mixColor(v: number): void {
    this.colors.forEach((c, i) => c.copy(this.fromColors[i]).lerp(this.toColors[i], v));
  }

  /** Draws crisp 2D canvases onto the faces: brushed graphite quadrants with their channel icon and label. */
  private paintTextures(): void {
    const THREE = this.THREE;
    if (!THREE || !this.renderer || !this.sizes.length) return;
    const aniso = this.renderer.capabilities.getMaxAnisotropy();
    const finish = this.finish();
    const mono = '"JetBrains Mono Variable", "JetBrains Mono", "Cascadia Mono", Consolas, monospace';

    const mix = (a: string, b: string, k: number): string => {
      const ca = new THREE.Color(a);
      const cb = new THREE.Color(b);
      return `#${ca.lerp(cb, k).getHexString()}`;
    };
    const shieldTop = Math.max(...this.sizes.map((s) => s.top));
    const shieldH = shieldTop - Math.min(...this.sizes.map((s) => s.bottom));

    this.capTex.forEach((t) => t.dispose());
    this.capTex = this.sizes.map((size, i) => {
      const q = QUADS[i];
      const cw = 1024;
      const ch = Math.max(Math.round((cw * size.h) / size.w), 64);
      const c = document.createElement('canvas');
      c.width = cw;
      c.height = ch;
      const ctx = c.getContext('2d')!;
      // One continuous top-to-bottom sheen across all four quadrants, so the assembled shield reads as one body.
      const k0 = (shieldTop - size.top) / shieldH;
      const k1 = (shieldTop - size.bottom) / shieldH;
      const grad = ctx.createLinearGradient(0, 0, 0, ch);
      grad.addColorStop(0, mix(finish.faceTop, finish.faceBottom, k0));
      grad.addColorStop(1, mix(finish.faceTop, finish.faceBottom, k1));
      ctx.fillStyle = grad;
      ctx.fillRect(0, 0, cw, ch);
      // Fine horizontal brushing (deterministic so repaints do not shimmer).
      let seed = 7 + i * 131;
      const rnd = (): number => {
        seed = (seed * 1664525 + 1013904223) >>> 0;
        return seed / 4294967296;
      };
      for (let y = 0; y < ch; y += 2) {
        ctx.fillStyle = rnd() > 0.5 ? `rgba(255,255,255,${rnd() * 0.035})` : `rgba(0,0,0,${rnd() * 0.05})`;
        ctx.fillRect(0, y, cw, 2);
      }
      // Channel mark: thin accent ring, icon, engraved label. Top quadrants are full near the cut (canvas bottom),
      // bottom quadrants taper away from it (canvas top), so the mark sits on the solid part of each.
      const mx = cw * 0.5;
      const my = q.sy > 0 ? ch * 0.6 : ch * 0.34;
      ctx.strokeStyle = CHANNEL_RING[q.channel];
      ctx.globalAlpha = 0.9;
      ctx.lineWidth = 6;
      ctx.beginPath();
      ctx.arc(mx, my, 118, 0, Math.PI * 2);
      ctx.stroke();
      ctx.globalAlpha = 1;
      const s = 6.2;
      ctx.save();
      ctx.translate(mx - 12 * s, my - 12 * s);
      ctx.scale(s, s);
      ctx.strokeStyle = '#f6f5f2';
      ctx.lineWidth = 1.5;
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';
      for (const d of CHANNEL_ICON_PATHS[q.channel]) ctx.stroke(new Path2D(d));
      ctx.restore();
      ctx.fillStyle = finish.label;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'alphabetic';
      ctx.font = `600 46px ${mono}`;
      (ctx as CanvasRenderingContext2D & { letterSpacing: string }).letterSpacing = '10px';
      ctx.fillText(q.label, mx + 5, my + 190);
      (ctx as CanvasRenderingContext2D & { letterSpacing: string }).letterSpacing = '0px';

      const tex = new THREE.CanvasTexture(c);
      tex.colorSpace = THREE.SRGBColorSpace;
      tex.anisotropy = aniso;
      tex.minFilter = THREE.LinearMipmapLinearFilter;
      tex.needsUpdate = true;
      return tex;
    });
    this.capMats.forEach((m, i) => {
      m.map = this.capTex[i];
      // The status glow is modulated by the same print, so brushing and marks stay visible through it.
      m.emissiveMap = this.capTex[i];
      m.needsUpdate = true;
    });
    this.renderFrame();
  }

  private startLoop(): void {
    // Reduced motion renders on demand only (no idle drift, no pointer tilt, no springs).
    if (this.raf || !this.ready || document.hidden || this.reduced) return;
    this.lastTs = performance.now();
    const tick = (ts: number): void => {
      this.raf = 0;
      const dt = Math.min((ts - this.lastTs) / 1000, 0.1);
      this.lastTs = ts;
      this.clock += dt;
      // Critically damped follow, so the shield eases toward the pointer instead of snapping.
      const k = 1 - Math.exp(-dt * 6);
      this.px += (this.tx - this.px) * k;
      this.py += (this.ty - this.py) * k;
      this.renderFrame();
      if (!document.hidden && !this.destroyed && !this.reduced) this.raf = requestAnimationFrame(tick);
    };
    this.raf = requestAnimationFrame(tick);
  }

  private stopLoop(): void {
    if (this.raf) cancelAnimationFrame(this.raf);
    this.raf = 0;
  }

  private renderFrame(): void {
    if (!this.renderer || !this.scene || !this.camera || !this.shield || !this.root) return;
    const idle = this.reduced ? 0 : 1;
    const c = this.cur;
    const px = this.px * idle;
    const py = this.py * idle;

    // Whole shield: rest pose + pointer tilt + a hair of drift, like a product on a turntable.
    this.shield.rotation.set(
      c[0] + py * 0.2,
      c[1] + px * 0.4 + Math.sin(this.clock * 0.35) * 0.06 * idle,
      c[2],
      'YXZ',
    );
    this.shield.position.y = Math.sin(this.clock * 0.8) * 0.04 * idle;

    for (let i = 0; i < N; i++) {
      const o = Q_AT + i * Q_STRIDE;
      const p = this.pivots[i];
      // Pointer shears the quadrants against each other a little.
      const shear = px * QUADS[i].sy * 0.05;
      p.position.set(this.centres[i].x + c[o] + shear, this.centres[i].y + c[o + 1], c[o + 2]);
      p.rotation.set(c[o + 3], c[o + 4], c[o + 5]);
      const glow = Math.max(c[o + 6], 0);
      const colour = this.colors[i];
      const cap = this.capMats[i];
      cap.emissive.copy(colour).multiplyScalar(1.7);
      cap.emissiveIntensity = glow * GLOW;
      // A tinted quadrant has a softer clearcoat, so the status hue is not washed toward white by the studio reflections.
      cap.clearcoat = 1 - 0.55 * Math.min(glow, 1);
      const edge = this.edgeMats[i];
      edge.emissive.copy(colour);
      edge.emissiveIntensity = glow * EDGE_GLOW;
    }

    if (this.key) this.key.position.set(-3 + this.px * 5 * idle, 6 - this.py * 3 * idle, 6);
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
    document.documentElement.removeEventListener('mouseleave', this.onPointerLeave);
    this.motionQuery?.removeEventListener('change', this.onMotionPref);
    this.themeObserver?.disconnect();
    this.resizeObserver?.disconnect();
    this.t.destroy();
    this.colorMix.destroy();
    this.capTex.forEach((t) => t.dispose());
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
