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
  signal,
  viewChild,
} from '@angular/core';
import { animate, motionValue, type AnimationPlaybackControls } from 'motion';
import type * as ThreeNS from 'three';
import type { Channel, Priority } from '../../../../core/models/dashboard.models';

type Three = typeof ThreeNS;

/**
 * The AegisNotify brand mark, copied verbatim from the sidebar logo (sidebar.component.html).
 * It is parsed by three's SVGLoader and extruded, so the 3D object is this exact silhouette.
 */
const SHIELD_PATH = 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z';
const SHIELD_SVG = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="${SHIELD_PATH}"/></svg>`;

/** SVG units (viewBox 24) -> world units. The shield is 16 x 20 units, so 3.2 x 4.0 in the scene. */
const S = 0.2;
const SHIELD_CY = 12;
/** Horizontal cuts (SVG y) that divide the shield into four delivery bands, top to bottom. */
const CUTS = [2, 7, 11.8, 16.4, 22];
const BAND_LABELS = [
  ['01', 'INTAKE'],
  ['02', 'QUEUE'],
  ['03', 'DISPATCH'],
  ['04', 'OUTCOME'],
] as const;
const BANDS = BAND_LABELS.length;

const DEPTH = 0.2;
const BEVEL_T = 0.06;
const BEVEL_S = 0.05;
/** Z of the front cap of a band, measured from the shield mid-plane. */
const FRONT_Z = DEPTH / 2 + BEVEL_T;

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

/** Channel accent ring on the medallion (bright variants of the dashboard's channel hues). */
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
  discTop: string;
  discBottom: string;
}
const FINISH_LIGHT: Finish = {
  faceTop: '#34363e',
  faceBottom: '#121316',
  edge: 0xeef0f5,
  edgeRough: 0.14,
  label: 'rgba(244,243,240,0.7)',
  discTop: '#3c3f48',
  discBottom: '#0f1013',
};
const FINISH_DARK: Finish = {
  faceTop: '#444852',
  faceBottom: '#1b1c21',
  edge: 0xd4d9e2,
  edgeRough: 0.16,
  label: 'rgba(244,243,240,0.7)',
  discTop: '#4a4e59',
  discBottom: '#131418',
};

/** Status -> semantic colour token (mirrors notificationTone). */
function toneVar(status: string): string {
  switch (status) {
    case 'SENT':
      return '--status-online';
    case 'SENT_VIA_FALLBACK':
      return '--status-fallback';
    case 'FAILED':
    case 'FAILED_CRITICAL':
    case 'PROVIDER_A_FAIL':
    case 'PROVIDER_B_FAIL':
      return '--status-offline';
    case 'QUEUED':
    case 'PROCESSING':
      return '--status-warning';
    default:
      return '--status-neutral';
  }
}

/**
 * A stage is a full pose of the object. Flat layout (Float32Array):
 *   [0..2]  whole shield: pitch, yaw, roll
 *   [3..5]  medallion: lift off the face, pitch, yaw
 *   [6..]   per band (7 each): dx, dy, dz, pitch, yaw, roll, glow
 * Band transforms pivot around the band's own centre.
 */
type V3 = [number, number, number];
type BandPose = [number, number, number, number, number, number, number];
const REST_BAND: BandPose = [0, 0, 0, 0, 0, 0, 0];
const BAND_AT = 6;
const BAND_STRIDE = 7;
const POSE_LEN = BAND_AT + BANDS * BAND_STRIDE;

/** Scales how far bands travel/turn, so the stack always still reads as one shield (glow is not scaled). */
const AMP = 0.68;

function pose(g: V3, m: V3, bands: Partial<Record<number, BandPose>> = {}): Float32Array {
  const out = new Float32Array(POSE_LEN);
  out.set(g, 0);
  out.set(m, 3);
  for (let i = 0; i < BANDS; i++) {
    const b = (bands[i] ?? REST_BAND).map((v, k) => (k < 6 ? v * AMP : v));
    out.set(b, BAND_AT + i * BAND_STRIDE);
  }
  return out;
}

const REST_POSE = pose([0.02, 0.14, 0], [0.1, 0, 0]);
const POSES: Record<string, Float32Array> = {
  // Intake band lifts out and tips forward: the request was accepted.
  PENDING: pose([0.05, 0.28, 0], [0.18, 0, 0.12], {
    0: [-0.08, 0.2, 0.5, -0.18, 0.12, 0.04, 1],
    1: [0, 0.05, 0.06, 0, 0, 0, 0],
  }),
  // Queue band slides out to the right.
  QUEUED: pose([0.03, -0.26, 0], [0.2, 0, -0.12], {
    0: [0, 0.06, 0.05, 0, 0, 0, 0],
    1: [0.38, 0.04, 0.46, 0.04, -0.16, 0.06, 1],
  }),
  // Dispatch band comes forward, the call to the provider is in flight.
  PROCESSING: pose([-0.02, 0.36, 0], [0.34, 0.06, 0.3], {
    1: [-0.08, 0, 0.1, 0, 0, 0, 0],
    2: [-0.34, -0.04, 0.5, 0.08, 0.14, -0.07, 1],
    3: [0, -0.06, 0.05, 0, 0, 0, 0],
  }),
  // Dispatch band breaks away askew: primary (A) or secondary (B) provider failed.
  PROVIDER_A_FAIL: pose([-0.05, -0.34, 0], [0.26, 0, -0.2], {
    2: [0.52, -0.2, 0.28, 0.14, -0.22, 0.3, 1],
    3: [0.1, -0.12, 0.04, 0, 0, 0.08, 0],
  }),
  PROVIDER_B_FAIL: pose([-0.05, 0.34, 0], [0.26, 0, 0.2], {
    2: [-0.52, -0.2, 0.28, 0.14, 0.22, -0.3, 1],
    3: [-0.1, -0.12, 0.04, 0, 0, -0.08, 0],
  }),
  // Delivered: the shield is whole, the outcome tip steps forward.
  SENT: pose([0.02, 0.14, 0], [0.08, 0, 0.1], {
    3: [0, -0.02, 0.34, 0.02, 0, 0, 1],
  }),
  // Delivered through the secondary provider: the tip slides sideways off the main axis.
  SENT_VIA_FALLBACK: pose([0.03, -0.18, 0], [0.16, 0, -0.1], {
    2: [0.2, 0, 0.1, 0, -0.06, 0.05, 0.35],
    3: [0.52, -0.05, 0.3, 0.02, -0.12, -0.14, 1],
  }),
  // The outcome tip drops and twists away.
  FAILED: pose([-0.05, -0.3, 0], [0.2, 0.08, -0.22], {
    2: [0.05, -0.04, 0.05, 0, 0, 0.04, 0],
    3: [-0.22, -0.4, 0.3, 0.22, 0.12, -0.4, 1],
  }),
  FAILED_CRITICAL: pose([0.08, -0.42, 0], [0.3, 0.12, -0.3], {
    0: [0, 0.2, 0, 0.05, 0, 0.03, 0],
    1: [0.1, 0.05, 0.1, 0, 0.05, 0, 0],
    2: [0.3, -0.12, 0.22, 0.1, 0.2, 0.22, 0.7],
    3: [-0.46, -0.6, 0.45, 0.32, -0.2, -0.6, 1],
  }),
  CANCELLED: pose([0.04, 0.1, 0], [0.12, 0, 0.2], {
    0: [0, 0.14, 0.04, 0, 0, 0.02, 0],
    1: [0, 0.05, 0.02, 0, 0, 0, 0],
    2: [0, -0.05, 0.02, 0, 0, 0, 0],
    3: [0, -0.16, 0.04, 0, 0, -0.03, 0],
  }),
};

const SPRING = { type: 'spring', stiffness: 110, damping: 15, mass: 0.9 } as const;
const GLOW = 2.1;
const BLOB_W = 3.5;
const BLOB_H = 0.42;

/**
 * The AegisNotify shield as a studio-lit product object: the sidebar logo silhouette, extruded and cut into
 * four delivery bands (intake, queue, dispatch, outcome) with chrome-bevelled edges and a channel medallion
 * on the face. The `stage` input re-poses the bands (hover/select a timeline step), the pointer tilts the whole
 * object and sweeps the key light across it. The canvas is transparent: the shield sits directly on the page.
 */
@Component({
  selector: 'app-channel-shield',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div #stage class="stage" role="img" [attr.aria-label]="ariaLabel()">
      <canvas #canvas></canvas>
    </div>
  `,
  styleUrl: './channel-shield.component.scss',
})
export class ChannelShieldComponent {
  readonly channel = input.required<Channel>();
  readonly priority = input.required<Priority>();
  readonly stage = input.required<string>();

  protected readonly ariaLabel = signal('');

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly zone = inject(NgZone);
  private readonly destroyRef = inject(DestroyRef);
  private readonly canvasRef = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private readonly stageRef = viewChild.required<ElementRef<HTMLElement>>('stage');

  // Stage transitions run a single spring 0 -> 1 between two flat poses.
  private readonly t = motionValue(1);
  private readonly colorMix = motionValue(1);
  private running: AnimationPlaybackControls[] = [];
  private from = REST_POSE.slice();
  private to = REST_POSE;
  private readonly cur = REST_POSE.slice();

  private THREE?: Three;
  private renderer?: ThreeNS.WebGLRenderer;
  private scene?: ThreeNS.Scene;
  private camera?: ThreeNS.PerspectiveCamera;
  private root?: ThreeNS.Group;
  private shield?: ThreeNS.Group;
  private medallion?: ThreeNS.Group;
  private bands: ThreeNS.Group[] = [];
  private bandCy: number[] = [];
  private capMats: ThreeNS.MeshPhysicalMaterial[] = [];
  private bandSizes: { w: number; h: number; top: number; bottom: number }[] = [];
  private capTex: ThreeNS.CanvasTexture[] = [];
  private edgeMat?: ThreeNS.MeshPhysicalMaterial;
  private discMat?: ThreeNS.MeshPhysicalMaterial;
  private discTex?: ThreeNS.CanvasTexture;
  private key?: ThreeNS.DirectionalLight;
  private blob?: ThreeNS.Mesh;
  private blobTex?: ThreeNS.CanvasTexture;
  private pmrem?: ThreeNS.PMREMGenerator;
  private themeObserver?: MutationObserver;
  private resizeObserver?: ResizeObserver;
  private intersection?: IntersectionObserver;
  private motionQuery?: MediaQueryList;
  private raf = 0;
  private reduced = false;
  private destroyed = false;
  private ready = false;
  private visible = true;
  private fromColor?: ThreeNS.Color;
  private toColor?: ThreeNS.Color;
  private statusColor?: ThreeNS.Color;
  private xFrac = 0.5;
  private visW = 1;
  // Pointer relative to the shield: target (raw) and smoothed (what is rendered).
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

    // Re-print the medallion when the channel changes.
    effect(() => {
      const channel = this.channel();
      this.priority();
      this.ariaLabel.set(`${channel} notification, stage ${this.stage().replaceAll('_', ' ').toLowerCase()}`);
      if (this.ready) this.paintTextures(channel);
    });

    // Re-pose the bands for the requested stage.
    effect(() => {
      const stage = this.stage();
      if (this.ready) this.goToStage(stage);
    });

    this.destroyRef.onDestroy(() => this.teardown());
  }

  private onVisibility = (): void => {
    if (document.hidden) this.stopLoop();
    else this.startLoop();
  };

  private onMotionPref = (e: MediaQueryListEvent): void => {
    this.reduced = e.matches;
    this.goToStage(this.stage());
  };

  private onPointerMove = (e: PointerEvent): void => {
    if (this.reduced || e.pointerType === 'touch' || !this.renderer) return;
    const r = this.stageRef().nativeElement.getBoundingClientRect();
    const cx = r.left + r.width * this.xFrac;
    const cy = r.top + r.height * 0.5;
    this.tx = Math.min(Math.max((e.clientX - cx) / (r.width * 0.5), -1), 1);
    this.ty = Math.min(Math.max((e.clientY - cy) / (r.height * 0.5), -1), 1);
  };

  private onPointerLeave = (): void => {
    this.tx = 0;
    this.ty = 0;
  };

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

      // Key light casts the band-on-band shadows and follows the pointer, so highlights sweep the bevels.
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

      // Soft grounded shadow: the object floats, so the shadow is a baked, very soft ellipse beneath it.
      this.blobTex = this.makeBlobTexture();
      this.blob = new THREE.Mesh(
        new THREE.PlaneGeometry(1, 1),
        new THREE.MeshBasicMaterial({
          map: this.blobTex,
          transparent: true,
          depthWrite: false,
          toneMapped: false,
        }),
      );
      // Camera-facing ellipse just under the tip: reads as a soft contact shadow from any camera height.
      this.blob.position.set(0, -2.22, -0.3);
      this.blob.scale.set(BLOB_W, BLOB_H, 1);
      root.add(this.blob);

      const shield = new THREE.Group();
      this.shield = shield;
      root.add(shield);

      this.edgeMat = new THREE.MeshPhysicalMaterial({
        color: finish.edge,
        metalness: 1,
        roughness: finish.edgeRough,
        clearcoat: 0.4,
        clearcoatRoughness: 0.1,
      });

      this.buildBands(THREE, SVGLoader, toCreasedNormals);
      this.buildMedallion(THREE);

      this.statusColor = new THREE.Color();
      this.fromColor = new THREE.Color();
      this.toColor = new THREE.Color();

      this.paintTextures(this.channel());
      this.applyColor(this.stage(), false);
      const target = POSES[this.stage()] ?? REST_POSE;
      this.cur.set(target);
      this.from = target.slice();
      this.to = target;

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

      this.resizeObserver = new ResizeObserver(() => this.resize());
      this.resizeObserver.observe(this.stageRef().nativeElement);
      this.resize();

      this.intersection = new IntersectionObserver((entries) => {
        this.visible = entries.some((e) => e.isIntersecting);
        if (this.visible) this.startLoop();
        else this.stopLoop();
      });
      this.intersection.observe(this.stageRef().nativeElement);

      // The copy sits on top of the canvas, so the pointer is tracked on the window rather than on the canvas.
      window.addEventListener('pointermove', this.onPointerMove, { passive: true });
      document.documentElement.addEventListener('mouseleave', this.onPointerLeave, { passive: true });
      document.addEventListener('visibilitychange', this.onVisibility);

      this.ready = true;
      this.host.nativeElement.classList.add('is-live');
      this.renderFrame();
      if (!document.hidden) this.startLoop();
    } catch {
      // No WebGL (or context lost): the page copy stands on its own.
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
    this.edgeMat?.color.setHex(f.edge);
    if (this.edgeMat) this.edgeMat.roughness = f.edgeRough;
    this.paintTextures(this.channel());
    this.applyColor(this.stage(), false);
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
   * sampled along its Bezier/line segments, then clipped into horizontal bands and each band is extruded with a
   * rounded bevel (bevelOffset keeps the outer wall exactly on the path).
   */
  private buildBands(
    THREE: Three,
    SVGLoader: typeof import('three/examples/jsm/loaders/SVGLoader.js').SVGLoader,
    toCreasedNormals: typeof import('three/examples/jsm/utils/BufferGeometryUtils.js').toCreasedNormals,
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

    const clip = (
      poly: ThreeNS.Vector2[],
      inside: (p: ThreeNS.Vector2) => boolean,
      cross: (a: ThreeNS.Vector2, b: ThreeNS.Vector2) => ThreeNS.Vector2,
    ): ThreeNS.Vector2[] => {
      const out: ThreeNS.Vector2[] = [];
      for (let i = 0; i < poly.length; i++) {
        const a = poly[i];
        const b = poly[(i + 1) % poly.length];
        const ia = inside(a);
        const ib = inside(b);
        if (ia) out.push(a);
        if (ia !== ib) out.push(cross(a, b));
      }
      return out;
    };

    for (let i = 0; i < BANDS; i++) {
      const hi = i === 0 ? 10 : (SHIELD_CY - CUTS[i]) * S;
      const lo = i === BANDS - 1 ? -10 : (SHIELD_CY - CUTS[i + 1]) * S;
      let poly = clip(
        outline,
        (p) => p.y <= hi + 1e-9,
        (a, b) => new THREE.Vector2(a.x + ((b.x - a.x) * (hi - a.y)) / (b.y - a.y), hi),
      );
      poly = clip(
        poly,
        (p) => p.y >= lo - 1e-9,
        (a, b) => new THREE.Vector2(a.x + ((b.x - a.x) * (lo - a.y)) / (b.y - a.y), lo),
      );
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
      const cy = (minY + maxY) / 2;
      this.bandSizes.push({ w, h, top: maxY, bottom: minY });
      this.bandCy.push(cy);

      // Front/back caps map onto the band's own bounding box, so each band gets its own printed face.
      const uv = (v: number[], a: number, b: number, c: number): ThreeNS.Vector2[] =>
        [a, b, c].map(
          (k) => new THREE.Vector2((v[k * 3] - minX) / w, (v[k * 3 + 1] - minY) / h),
        );
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
      geo.translate(0, -cy, -DEPTH / 2);
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
      // Group 0 = front/back caps, group 1 = side walls + bevel (ExtrudeGeometry convention).
      const mesh = new THREE.Mesh(geo, [cap, this.edgeMat!]);
      mesh.castShadow = true;
      mesh.receiveShadow = true;
      const pivot = new THREE.Group();
      pivot.position.set(0, cy, 0);
      pivot.add(mesh);
      this.shield!.add(pivot);
      this.bands.push(pivot);
    }
  }

  /** Channel medallion: a chrome-rimmed puck with the channel icon, floating just off the face. */
  private buildMedallion(THREE: Three): void {
    const R = 0.84;
    const g = new THREE.Group();
    const puck = new THREE.Mesh(new THREE.CylinderGeometry(R, R, 0.12, 128), this.edgeMat!);
    puck.rotation.x = Math.PI / 2;
    puck.castShadow = true;
    g.add(puck);
    const rim = new THREE.Mesh(new THREE.TorusGeometry(R, 0.045, 32, 160), this.edgeMat!);
    rim.position.z = 0.06;
    rim.castShadow = true;
    g.add(rim);
    // Satin glass: glossy enough to catch a soft highlight, matte enough that the icon never washes out.
    this.discMat = new THREE.MeshPhysicalMaterial({
      roughness: 0.4,
      metalness: 0,
      clearcoat: 0.35,
      clearcoatRoughness: 0.3,
      envMapIntensity: 0.55,
    });
    const disc = new THREE.Mesh(new THREE.CircleGeometry(R - 0.02, 128), this.discMat);
    disc.position.z = 0.0625;
    g.add(disc);
    // Sits on the face around the 2nd/3rd band seam, where the brand mark's own "!" would read.
    g.position.set(0, (SHIELD_CY - 10.7) * S, FRONT_Z + 0.06);
    this.medallion = g;
    this.shield!.add(g);
  }

  /** Soft radial falloff used as the baked shadow under the object. */
  private makeBlobTexture(): ThreeNS.CanvasTexture {
    const THREE = this.THREE!;
    const c = document.createElement('canvas');
    c.width = c.height = 256;
    const ctx = c.getContext('2d')!;
    const g = ctx.createRadialGradient(128, 128, 0, 128, 128, 128);
    g.addColorStop(0, 'rgba(20,20,26,0.4)');
    g.addColorStop(0.5, 'rgba(20,20,26,0.15)');
    g.addColorStop(1, 'rgba(20,20,26,0)');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, 256, 256);
    const tex = new THREE.CanvasTexture(c);
    tex.colorSpace = THREE.SRGBColorSpace;
    return tex;
  }

  private resize(): void {
    if (!this.renderer || !this.camera || !this.root) return;
    const el = this.stageRef().nativeElement;
    const w = Math.max(el.clientWidth, 1);
    const h = Math.max(el.clientHeight, 1);
    // Cap the backing store: a hero this wide on a hi-dpi screen would otherwise be millions of extra pixels.
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2, Math.sqrt(5_500_000 / (w * h))));
    this.renderer.setSize(w, h, false);
    const aspect = w / h;
    this.camera.aspect = aspect;
    // CSS owns the composition: --shield-x is where (0..1) the shield's centre sits across the nearest
    // [data-shield-anchor] element (the hero's text frame); the canvas itself may be much wider than that.
    const raw = parseFloat(getComputedStyle(this.host.nativeElement).getPropertyValue('--shield-x'));
    const frac = Number.isFinite(raw) ? raw : 0.5;
    const anchor = this.host.nativeElement.closest<HTMLElement>('[data-shield-anchor]');
    if (anchor) {
      const cr = el.getBoundingClientRect();
      const ar = anchor.getBoundingClientRect();
      this.xFrac = (ar.left + ar.width * frac - cr.left) / Math.max(cr.width, 1);
    } else {
      this.xFrac = frac;
    }
    // Visible world height at the shield's plane: the shield fills ~84% of it, widened if the room to its left is tight.
    const leftRoom = 2.6 / Math.max(Math.min(this.xFrac, 1 - this.xFrac), 0.1);
    const visH = Math.max(4.9, leftRoom / aspect);
    const dist = visH / (2 * Math.tan((this.camera.fov * Math.PI) / 360));
    this.visW = visH * aspect;
    this.camera.position.set(0, 0.14, dist);
    this.camera.lookAt(0, 0.02, 0);
    this.camera.updateProjectionMatrix();
    this.root.position.x = (this.xFrac - 0.5) * this.visW;
    this.renderFrame();
  }

  private goToStage(stage: string): void {
    this.running.forEach((c) => c.stop());
    this.running = [];
    const next = POSES[stage] ?? REST_POSE;
    this.applyColor(stage, !this.reduced);
    this.from = this.cur.slice();
    this.to = next;
    if (this.reduced) {
      this.cur.set(next);
      this.t.set(1);
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

  private applyColor(stage: string, animated: boolean): void {
    if (!this.THREE || !this.toColor || !this.fromColor || !this.statusColor) return;
    this.fromColor.copy(this.statusColor);
    this.toColor.copy(this.cssColor(toneVar(stage)));
    if (!animated) {
      this.statusColor.copy(this.toColor);
      return;
    }
    this.colorMix.set(0);
    this.running.push(animate(this.colorMix, 1, { duration: 0.45, ease: 'easeOut' }));
  }

  private mixColor(v: number): void {
    if (!this.statusColor || !this.fromColor || !this.toColor) return;
    this.statusColor.copy(this.fromColor).lerp(this.toColor, v);
  }

  /** Draws crisp 2D canvases onto the faces: brushed graphite bands with their stage label, and the medallion. */
  private paintTextures(channel: Channel): void {
    const THREE = this.THREE;
    if (!THREE || !this.renderer || !this.bandSizes.length) return;
    const aniso = this.renderer.capabilities.getMaxAnisotropy();
    const finish = this.finish();
    const display = '"Outfit Variable", Outfit, "Inter Variable", system-ui, sans-serif';
    const mono = '"JetBrains Mono Variable", "JetBrains Mono", "Cascadia Mono", Consolas, monospace';

    const make = (w: number, h: number, draw: (ctx: CanvasRenderingContext2D) => void): ThreeNS.CanvasTexture => {
      const c = document.createElement('canvas');
      c.width = w;
      c.height = h;
      draw(c.getContext('2d')!);
      const tex = new THREE.CanvasTexture(c);
      tex.colorSpace = THREE.SRGBColorSpace;
      tex.anisotropy = aniso;
      tex.minFilter = THREE.LinearMipmapLinearFilter;
      tex.needsUpdate = true;
      return tex;
    };

    const shieldTop = this.bandSizes[0].top;
    const shieldH = shieldTop - this.bandSizes[BANDS - 1].bottom;
    const mix = (a: string, b: string, k: number): string => {
      const ca = new THREE.Color(a);
      const cb = new THREE.Color(b);
      return `#${ca.lerp(cb, k).getHexString()}`;
    };

    this.capTex.forEach((t) => t.dispose());
    this.capTex = this.bandSizes.map((size, i) => {
      const cw = 1280;
      const ch = Math.max(Math.round((cw * size.h) / size.w), 64);
      return make(cw, ch, (ctx) => {
        // One continuous top-to-bottom sheen across all four bands, so the assembled shield reads as one body.
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
        // Stage label: small, engraved-looking, kept clear of the medallion.
        const [num, name] = BAND_LABELS[i];
        ctx.fillStyle = finish.label;
        ctx.textBaseline = 'alphabetic';
        const centred = i === 0 || i === BANDS - 1;
        ctx.textAlign = centred ? 'center' : 'left';
        const x = centred ? cw / 2 : cw * 0.1;
        const yNum = centred ? (i === 0 ? ch * 0.64 : ch * 0.3) : ch * 0.5;
        ctx.font = `700 54px ${display}`;
        ctx.fillText(num, x, yNum);
        ctx.font = `600 31px ${mono}`;
        (ctx as CanvasRenderingContext2D & { letterSpacing: string }).letterSpacing = '7px';
        ctx.fillText(name, x, yNum + 46);
        (ctx as CanvasRenderingContext2D & { letterSpacing: string }).letterSpacing = '0px';
      });
    });
    this.capMats.forEach((m, i) => {
      m.map = this.capTex[i];
      // The status glow is modulated by the same print, so brushing and labels stay visible through it.
      m.emissiveMap = this.capTex[i];
      m.needsUpdate = true;
    });

    // Medallion disc: graphite glass, a thin channel-colour ring, the channel icon.
    this.discTex?.dispose();
    this.discTex = make(1024, 1024, (ctx) => {
      const g = ctx.createRadialGradient(380, 320, 40, 512, 512, 600);
      g.addColorStop(0, finish.discTop);
      g.addColorStop(1, finish.discBottom);
      ctx.fillStyle = g;
      ctx.fillRect(0, 0, 1024, 1024);
      ctx.strokeStyle = CHANNEL_RING[channel];
      ctx.globalAlpha = 0.9;
      ctx.lineWidth = 9;
      ctx.beginPath();
      ctx.arc(512, 512, 452, 0, Math.PI * 2);
      ctx.stroke();
      ctx.globalAlpha = 1;
      const s = 29;
      ctx.save();
      ctx.translate(512 - 12 * s, 512 - 12 * s);
      ctx.scale(s, s);
      ctx.strokeStyle = '#f6f5f2';
      ctx.lineWidth = 1.5;
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';
      for (const d of CHANNEL_ICON_PATHS[channel]) ctx.stroke(new Path2D(d));
      ctx.restore();
    });
    if (this.discMat) {
      this.discMat.map = this.discTex;
      this.discMat.needsUpdate = true;
    }
    this.renderFrame();
  }

  private startLoop(): void {
    if (this.raf || !this.ready || document.hidden || !this.visible) return;
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
      if (!document.hidden && !this.destroyed && this.visible) this.raf = requestAnimationFrame(tick);
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
    const float = Math.sin(this.clock * 0.8);
    const px = this.px * idle;
    const py = this.py * idle;

    // Whole shield: stage pose + pointer tilt + a hair of drift, like a product on a turntable.
    this.shield.rotation.set(
      c[0] + py * 0.26,
      c[1] + px * 0.5 + Math.sin(this.clock * 0.35) * 0.05 * idle,
      c[2],
      'YXZ',
    );
    this.shield.position.y = float * 0.05 * idle;

    for (let i = 0; i < BANDS; i++) {
      const o = BAND_AT + i * BAND_STRIDE;
      const b = this.bands[i];
      // Pointer shears the bands against each other: moving across the page slides the stack.
      const shear = px * (i - (BANDS - 1) / 2) * 0.07;
      b.position.set(c[o] + shear, this.bandCy[i] + c[o + 1], c[o + 2]);
      b.rotation.set(c[o + 3], c[o + 4], c[o + 5]);
      const glow = Math.max(c[o + 6], 0);
      const mat = this.capMats[i];
      if (this.statusColor) {
        mat.emissive.copy(this.statusColor);
        // Brighten the tone a touch: it is a light, not a print.
        mat.emissive.multiplyScalar(1.15);
      }
      mat.emissiveIntensity = glow * GLOW;
    }

    if (this.medallion) {
      // The coin floats clear of whichever band is furthest forward (translation plus the tilt over its radius).
      let clear = 0;
      for (let i = 0; i < BANDS; i++) {
        const o = BAND_AT + i * BAND_STRIDE;
        clear = Math.max(clear, c[o + 2] + 0.84 * (Math.abs(Math.sin(c[o + 4])) + Math.abs(Math.sin(c[o + 3]))) + 0.1);
      }
      this.medallion.position.z = FRONT_Z + 0.06 + Math.max(c[3], clear);
      this.medallion.rotation.set(c[4] + py * 0.1, c[5] + px * 0.18, 0);
    }

    if (this.key) this.key.position.set(-3 + this.px * 5 * idle, 6 - this.py * 3 * idle, 6);

    if (this.blob) {
      const lift = float * idle;
      this.blob.scale.set(BLOB_W - lift * 0.12, BLOB_H - lift * 0.02, 1);
      (this.blob.material as ThreeNS.MeshBasicMaterial).opacity = 1 - lift * 0.1;
    }
    this.renderer.render(this.scene, this.camera);
  }

  private teardown(): void {
    this.destroyed = true;
    this.stopLoop();
    this.running.forEach((c) => c.stop());
    this.running = [];
    document.removeEventListener('visibilitychange', this.onVisibility);
    window.removeEventListener('pointermove', this.onPointerMove);
    document.documentElement.removeEventListener('mouseleave', this.onPointerLeave);
    this.motionQuery?.removeEventListener('change', this.onMotionPref);
    this.themeObserver?.disconnect();
    this.intersection?.disconnect();
    this.resizeObserver?.disconnect();
    this.t.destroy();
    this.colorMix.destroy();
    this.capTex.forEach((t) => t.dispose());
    this.discTex?.dispose();
    this.blobTex?.dispose();
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
