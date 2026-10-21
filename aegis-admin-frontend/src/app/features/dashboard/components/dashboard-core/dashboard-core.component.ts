import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  NgZone,
  OnDestroy,
  afterNextRender,
  computed,
  inject,
  input,
  signal,
  viewChild,
  viewChildren,
} from '@angular/core';
import { Router } from '@angular/router';
import { animate, type AnimationPlaybackControls } from 'motion';
import type * as ThreeNS from 'three';

type Three = typeof ThreeNS;

export type CoreHotspotId = 'latency' | 'success' | 'requests' | 'critical' | 'fallbacks' | 'circuits';

/** One real dashboard signal, revealed as a floating glass card when its node is hovered or focused. */
export interface CoreHotspot {
  id: CoreHotspotId;
  label: string;
  value: string;
  unit?: string;
  tone: 'neutral' | 'ok' | 'critical';
}

/**
 * The brand glyph (sidebar.component.html, viewBox 24) in 3D: hub at (12,12), six satellites on a ring.
 * Solid satellites are the four filled circles, the hollow ones the two outlines. Each satellite carries one signal.
 * `z` alternates front/back so the flat icon gains depth without losing its silhouette.
 */
interface NodeSpec {
  id: CoreHotspotId;
  x: number;
  y: number;
  solid: boolean;
  z: number;
}
const NODES: readonly NodeSpec[] = [
  { id: 'latency', x: 19, y: 12, solid: true, z: 0.3 },
  { id: 'success', x: 15.5, y: 6.06, solid: true, z: -0.3 },
  { id: 'requests', x: 8.5, y: 6.06, solid: true, z: 0.3 },
  { id: 'critical', x: 5, y: 12, solid: false, z: -0.3 },
  { id: 'fallbacks', x: 8.5, y: 17.94, solid: true, z: 0.3 },
  { id: 'circuits', x: 15.5, y: 17.94, solid: false, z: -0.3 },
];

/** SVG units -> world units, and the 3D sizes derived from the icon's radii (hub 2.6, solid 1.7, hollow 1.5). */
const S = 0.3;
const HUB_R = 0.64;
const SAT_R = 0.4;
const RING_R = 0.37;
/** Half-extents of the whole silhouette in world units, used to frame it and size the click target. */
const EXTENT_W = 5.1;
const EXTENT_H = 4.5;
const CLICK_R = 2.6;

const CRIMSON = 0xf4094c;
const EASE_OUT: [number, number, number, number] = [0.22, 1, 0.36, 1];

interface NodeRuntime {
  spec: NodeSpec;
  group: ThreeNS.Group;
  body: ThreeNS.Mesh;
  mat: ThreeNS.MeshPhysicalMaterial;
  pos: ThreeNS.Vector3;
  start: number;
  end: number;
  dir: ThreeNS.Vector3;
  pulse: ThreeNS.Mesh;
  hl: number;
  hlCtrl?: AnimationPlaybackControls;
  sx: number;
  sy: number;
}

/**
 * "The Orchestration Core": a levitating crystal build of the brand's hub-and-spoke glyph inside a dark vitrine.
 * The whole core is one giant button to /notifications; hovering or focusing a satellite reveals one glass card.
 * Owned by the Dashboard: own renderer/canvas, fully disposed with the route.
 */
@Component({
  selector: 'app-dashboard-core',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard-core.component.html',
  styleUrl: './dashboard-core.component.scss',
})
export class DashboardCoreComponent implements OnDestroy {
  readonly hotspots = input.required<CoreHotspot[]>();

  protected readonly nodeIds = NODES.map((n) => n.id);
  protected readonly unsupported = signal(false);
  private readonly cardId = signal<CoreHotspotId>('latency');
  protected readonly card = computed(() => this.hotspots().find((h) => h.id === this.cardId()));
  protected readonly cardSide = signal<'left' | 'right'>('right');

  private readonly router = inject(Router);
  private readonly zone = inject(NgZone);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly vitrine = viewChild.required<ElementRef<HTMLElement>>('vitrine');
  private readonly canvasRef = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private readonly coreBtn = viewChild.required<ElementRef<HTMLButtonElement>>('coreBtn');
  private readonly spotBtns = viewChildren<ElementRef<HTMLButtonElement>>('spot');
  private readonly anchorRef = viewChild.required<ElementRef<HTMLElement>>('anchor');
  private readonly cardRef = viewChild.required<ElementRef<HTMLElement>>('cardEl');
  private readonly placardRef = viewChild.required<ElementRef<HTMLElement>>('placard');
  private readonly ctaRef = viewChild.required<ElementRef<HTMLElement>>('cta');
  private readonly flashRef = viewChild.required<ElementRef<HTMLElement>>('flash');
  private readonly floorRef = viewChild.required<ElementRef<HTMLElement>>('floor');

  private THREE?: Three;
  private renderer?: ThreeNS.WebGLRenderer;
  private scene?: ThreeNS.Scene;
  private camera?: ThreeNS.PerspectiveCamera;
  private core?: ThreeNS.Group;
  private hub?: ThreeNS.Mesh;
  private shell?: ThreeNS.LineSegments;
  private innerMat?: ThreeNS.MeshStandardMaterial;
  private pmrem?: ThreeNS.PMREMGenerator;
  private nodes: NodeRuntime[] = [];
  private resizeObserver?: ResizeObserver;
  private intersection?: IntersectionObserver;
  private motionQuery?: MediaQueryList;
  private raf = 0;
  private lastTs = 0;
  private clock = 0;
  private reduced = false;
  private inView = true;
  private ready = false;
  private destroyed = false;
  private leaving = false;
  private dist = 14;
  private w = 1;
  private h = 1;
  private cardW = 208;
  private cardH = 112;
  private activeId: CoreHotspotId | null = null;
  private hideTimer = 0;
  private cardCtrl?: AnimationPlaybackControls;
  private hoverCtrl?: AnimationPlaybackControls;
  private rushCtrl?: AnimationPlaybackControls;
  private rush = 0;
  private hover = 0;
  private tx = 0;
  private ty = 0;
  private px = 0;
  private py = 0;
  private readonly tmp: { v?: ThreeNS.Vector3 } = {};

  constructor() {
    afterNextRender(() => {
      this.zone.runOutsideAngular(() => void this.init());
    });
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    window.clearTimeout(this.hideTimer);
    this.cardCtrl?.stop();
    this.hoverCtrl?.stop();
    this.rushCtrl?.stop();
    this.nodes.forEach((n) => n.hlCtrl?.stop());
    this.teardown();
  }

  protected spotLabel(id: CoreHotspotId): string {
    const h = this.hotspots().find((s) => s.id === id);
    return h ? `${h.label}: ${h.value}${h.unit ? ' ' + h.unit : ''}. Open notifications` : 'Open notifications';
  }

  // ---- interaction -------------------------------------------------------------------------------------------

  /** Giant-button behaviour: rush toward the camera, flash, then navigate. Reduced motion navigates at once. */
  protected async activate(): Promise<void> {
    if (this.leaving) return;
    this.leaving = true;
    window.clearTimeout(this.hideTimer);
    if (this.reduced || document.hidden || !this.ready) {
      void this.router.navigate(['/notifications']);
      return;
    }
    this.cardCtrl?.stop();
    const fading = [
      this.placardRef().nativeElement,
      this.cardRef().nativeElement,
      ...this.spotBtns().map((b) => b.nativeElement),
    ];
    this.zone.runOutsideAngular(() => {
      for (const el of fading) animate(el, { opacity: [1, 0] }, { duration: 0.28, ease: 'easeOut' });
      animate(this.flashRef().nativeElement, { opacity: [0, 1] }, { duration: 0.3, delay: 0.42, ease: 'easeIn' });
    });
    this.rushCtrl = animate(0, 1, {
      duration: 0.74,
      ease: [0.6, 0.04, 0.9, 0.4],
      onUpdate: (v) => {
        this.rush = v;
        if (!this.raf) this.renderFrame();
      },
    });
    try {
      await this.rushCtrl.finished;
    } catch {
      return;
    }
    if (this.destroyed) return;
    const ok = await this.router.navigate(['/notifications']);
    if (!ok && !this.destroyed) this.cancelLeave(fading);
  }

  private cancelLeave(fading: HTMLElement[]): void {
    this.leaving = false;
    this.rush = 0;
    for (const el of fading) animate(el, { opacity: 1 }, { duration: 0.3 });
    animate(this.flashRef().nativeElement, { opacity: 0 }, { duration: 0.3 });
    this.renderFrame();
  }

  protected onSpotEnter(id: CoreHotspotId, e: PointerEvent): void {
    if (e.pointerType === 'touch') return;
    this.showCard(id);
  }

  protected onSpotLeave(id: CoreHotspotId): void {
    this.scheduleHide(id);
  }

  protected onSpotClick(id: CoreHotspotId, e: MouseEvent): void {
    // Touch has no hover: the first tap reveals the card, a second tap on the same node opens Notifications.
    if ((e as PointerEvent).pointerType === 'touch' && this.activeId !== id) {
      this.showCard(id);
      return;
    }
    void this.activate();
  }

  protected onSpotFocus(id: CoreHotspotId): void {
    this.showCard(id);
  }

  protected onSpotBlur(id: CoreHotspotId): void {
    this.scheduleHide(id);
  }

  protected onCoreEnter(): void {
    this.setCoreHover(1);
  }

  protected onCoreLeave(): void {
    this.setCoreHover(0);
  }

  protected onPointerMove(e: PointerEvent): void {
    if (this.reduced || e.pointerType === 'touch') return;
    const r = this.vitrine().nativeElement.getBoundingClientRect();
    this.tx = Math.min(Math.max(((e.clientX - r.left) / r.width) * 2 - 1, -1), 1);
    this.ty = Math.min(Math.max(((e.clientY - r.top) / r.height) * 2 - 1, -1), 1);
  }

  protected onPointerLeave(): void {
    this.tx = 0;
    this.ty = 0;
  }

  private setCoreHover(target: number): void {
    this.hoverCtrl?.stop();
    const cta = this.ctaRef().nativeElement;
    if (this.reduced) {
      this.hover = target;
      cta.style.opacity = String(target);
      this.renderFrame();
      return;
    }
    this.zone.runOutsideAngular(() => {
      this.hoverCtrl = animate(this.hover, target, {
        duration: 0.4,
        ease: EASE_OUT,
        onUpdate: (v) => {
          this.hover = v;
          if (!this.raf) this.renderFrame();
        },
      });
      animate(cta, { opacity: target, x: target ? 0 : -6 }, { duration: 0.3, ease: EASE_OUT });
    });
  }

  private showCard(id: CoreHotspotId): void {
    if (!this.ready) return;
    window.clearTimeout(this.hideTimer);
    const was = this.activeId;
    if (was === id) return;
    this.activeId = id;
    this.cardId.set(id);
    if (was) this.setHighlight(was, 0);
    this.setHighlight(id, 1);
    const node = this.nodes.find((n) => n.spec.id === id);
    if (node) this.cardSide.set(node.sx > this.w / 2 ? 'right' : 'left');
    // Let Angular paint the new label/value before the card is measured and revealed.
    queueMicrotask(() => {
      if (this.activeId !== id) return;
      const el = this.cardRef().nativeElement;
      this.cardW = el.offsetWidth || this.cardW;
      this.cardH = el.offsetHeight || this.cardH;
      this.positionCard();
      el.style.visibility = 'visible';
      this.cardCtrl?.stop();
      if (this.reduced) {
        el.style.opacity = '1';
        el.style.transform = 'none';
        return;
      }
      this.zone.runOutsideAngular(() => {
        this.cardCtrl = animate(
          el,
          { opacity: [0, 1], scale: [0.94, 1], y: [8, 0] },
          { duration: 0.26, ease: EASE_OUT },
        );
      });
    });
  }

  private scheduleHide(id: CoreHotspotId): void {
    window.clearTimeout(this.hideTimer);
    // A short grace period so sliding between neighbouring hit areas never flickers the card.
    this.hideTimer = window.setTimeout(() => {
      if (this.activeId !== id) return;
      this.activeId = null;
      this.setHighlight(id, 0);
      const el = this.cardRef().nativeElement;
      this.cardCtrl?.stop();
      if (this.reduced) {
        el.style.opacity = '0';
        el.style.visibility = 'hidden';
        return;
      }
      this.zone.runOutsideAngular(() => {
        const ctrl = animate(el, { opacity: 0, scale: 0.97, y: 4 }, { duration: 0.18, ease: 'easeIn' });
        this.cardCtrl = ctrl;
        ctrl.finished
          .then(() => {
            if (this.activeId === null) el.style.visibility = 'hidden';
          })
          .catch(() => undefined);
      });
    }, 90);
  }

  /** Eases a node's highlight (scale + glow) with motion; the render loop reads `hl` every frame. */
  private setHighlight(id: CoreHotspotId, target: number): void {
    const node = this.nodes.find((n) => n.spec.id === id);
    if (!node) return;
    node.hlCtrl?.stop();
    if (this.reduced) {
      node.hl = target;
      this.renderFrame();
      return;
    }
    this.zone.runOutsideAngular(() => {
      node.hlCtrl = animate(node.hl, target, {
        duration: 0.4,
        ease: EASE_OUT,
        onUpdate: (v) => {
          node.hl = v;
          if (!this.raf) this.renderFrame();
        },
      });
    });
    const spot = this.spotBtns().find((b) => b.nativeElement.dataset['id'] === id);
    const ring = spot?.nativeElement.querySelector<HTMLElement>('.ring');
    if (ring) {
      animate(ring, { opacity: target, scale: target ? 1 : 0.6 }, { duration: 0.3, ease: EASE_OUT });
    }
  }

  private positionCard(): void {
    if (!this.activeId) return;
    const node = this.nodes.find((n) => n.spec.id === this.activeId);
    if (!node) return;
    const gap = 50;
    const right = node.sx > this.w / 2;
    let x = right ? node.sx + gap : node.sx - gap - this.cardW;
    x = Math.min(Math.max(x, 12), Math.max(this.w - this.cardW - 12, 12));
    const y = Math.min(Math.max(node.sy - this.cardH / 2, 12), Math.max(this.h - this.cardH - 12, 12));
    this.anchorRef().nativeElement.style.transform = `translate3d(${x.toFixed(1)}px, ${y.toFixed(1)}px, 0)`;
  }

  // ---- scene -------------------------------------------------------------------------------------------------

  private async init(): Promise<void> {
    try {
      const THREE = await import('three');
      if (this.destroyed) return;
      this.THREE = THREE;
      this.tmp.v = new THREE.Vector3();

      const canvas = this.canvasRef().nativeElement;
      const renderer = new THREE.WebGLRenderer({
        canvas,
        antialias: true,
        alpha: true,
        powerPreference: 'high-performance',
      });
      renderer.setClearColor(0x000000, 0);
      renderer.outputColorSpace = THREE.SRGBColorSpace;
      renderer.toneMapping = THREE.NeutralToneMapping;
      renderer.toneMappingExposure = 1;
      this.renderer = renderer;

      const scene = new THREE.Scene();
      this.scene = scene;
      this.pmrem = new THREE.PMREMGenerator(renderer);
      const studio = this.buildStudio(THREE);
      // Sigma 0: keep the softbox edges razor sharp so facets read as crisp streaks, not haze.
      scene.environment = this.pmrem.fromScene(studio, 0).texture;
      studio.traverse((o) => {
        const m = o as ThreeNS.Mesh;
        m.geometry?.dispose();
        (m.material as ThreeNS.Material | undefined)?.dispose();
      });
      scene.environmentIntensity = 1;

      const camera = new THREE.PerspectiveCamera(24, 1, 0.1, 80);
      camera.position.set(0, 0, 14);
      this.camera = camera;

      const key = new THREE.DirectionalLight(0xfff1f4, 1.1);
      key.position.set(-4, 5, 7);
      scene.add(key);
      const rim = new THREE.DirectionalLight(0xff4d7e, 1.4);
      rim.position.set(5, -2, -6);
      scene.add(rim);

      this.core = new THREE.Group();
      scene.add(this.core);
      this.buildCore(THREE);

      this.motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
      this.reduced = this.motionQuery.matches;
      this.motionQuery.addEventListener('change', this.onMotionPref);

      this.resizeObserver = new ResizeObserver(() => this.resize());
      this.resizeObserver.observe(this.vitrine().nativeElement);
      this.intersection = new IntersectionObserver(([entry]) => {
        this.inView = entry?.isIntersecting ?? true;
        if (this.inView) this.startLoop();
        else this.stopLoop();
      });
      this.intersection.observe(this.vitrine().nativeElement);
      document.addEventListener('visibilitychange', this.onVisibility);

      this.ready = true;
      this.resize();
      this.host.nativeElement.classList.add('is-live');
      this.entrance();
      if (!document.hidden) this.startLoop();
    } catch {
      // No WebGL (or lost context): the flat glyph and the same click target stand in.
      this.zone.run(() => this.unsupported.set(true));
      this.teardown();
    }
  }

  /** Dark studio with hard-edged softboxes (one crimson) and pin lights: gives the glass something sharp to refract. */
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
      const v = 0.025 + Math.max(y, 0) * 0.07;
      colors.set([v, v, v * 1.2], i * 3);
    }
    dome.geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3));
    env.add(dome);

    const box = (w: number, h: number, x: number, y: number, z: number, r: number, g: number, b: number): void => {
      const m = new THREE.Mesh(
        new THREE.PlaneGeometry(w, h),
        new THREE.MeshBasicMaterial({ side: THREE.DoubleSide, toneMapped: false }),
      );
      (m.material as ThreeNS.MeshBasicMaterial).color.setRGB(r, g, b);
      m.position.set(x, y, z);
      m.lookAt(0, 0, 0);
      env.add(m);
    };
    box(14, 6, 0, 14, 4, 6, 6, 6.6); // overhead softbox
    box(2.2, 18, -13, 2, 4, 9, 9, 10); // tall cool strip, left
    box(2.2, 18, 13, 0, 2, 6.5, 0.7, 2); // tall crimson strip, right
    box(10, 2.2, 0, -10, 6, 3.2, 3.2, 3.6); // low bounce
    box(12, 5, 0, 3, -16, 1.6, 1.6, 1.8); // back wash: backlights the facets
    box(1.2, 1.2, -7, 7, 12, 22, 22, 24); // pin lights: the sparkle on edges
    box(1, 1, 8, 5, 11, 20, 8, 12);
    box(1, 1, -4, -6, 10, 16, 16, 18);
    return env;
  }

  private buildCore(THREE: Three): void {
    const core = this.core!;

    // Hub: a clear faceted crystal, with a crimson ember inside it that shows through the facets.
    // A stretched octahedron: a cut-gem prism whose long facets catch the softbox streaks as it turns.
    const hubGeo = new THREE.OctahedronGeometry(HUB_R * 1.12, 0);
    hubGeo.scale(1, 1.32, 1);
    // Smoked glass built from alpha + sharp specular (true transmission washes out on a transparent canvas).
    const hubMat = new THREE.MeshPhysicalMaterial({
      color: 0x20232c,
      metalness: 0.25,
      roughness: 0.03,
      transparent: true,
      opacity: 0.42,
      side: THREE.DoubleSide,
      depthWrite: false,
      envMapIntensity: 2.4,
      clearcoat: 1,
      clearcoatRoughness: 0.02,
      iridescence: 0.7,
      iridescenceIOR: 1.6,
      flatShading: true,
    });
    const hub = new THREE.Mesh(hubGeo, hubMat);
    hub.add(
      new THREE.LineSegments(
        new THREE.EdgesGeometry(hubGeo),
        new THREE.LineBasicMaterial({ color: 0xffd9e3, transparent: true, opacity: 0.5, toneMapped: false }),
      ),
    );
    this.innerMat = new THREE.MeshStandardMaterial({
      color: 0x3a0412,
      emissive: new THREE.Color(CRIMSON),
      emissiveIntensity: 2.4,
      roughness: 0.4,
      flatShading: true,
    });
    const ember = new THREE.Mesh(new THREE.OctahedronGeometry(HUB_R * 0.4, 0), this.innerMat);
    hub.add(ember);
    core.add(hub);
    this.hub = hub;

    // Hologram shell: a wider facet wireframe that turns against the crystal.
    this.shell = new THREE.LineSegments(
      new THREE.EdgesGeometry(new THREE.IcosahedronGeometry(HUB_R * 1.55, 1)),
      new THREE.LineBasicMaterial({ color: 0xff7a9c, transparent: true, opacity: 0.2, toneMapped: false }),
    );
    core.add(this.shell);

    const spokeMat = new THREE.MeshBasicMaterial({ color: 0xff3b6e, toneMapped: false });
    const pulseMat = new THREE.MeshBasicMaterial({ color: 0xffe3ea, toneMapped: false });
    const gemGeo = new THREE.IcosahedronGeometry(SAT_R, 1);
    const ringGeo = new THREE.TorusGeometry(RING_R, 0.024, 20, 96);
    const up = new THREE.Vector3(0, 1, 0);

    for (const spec of NODES) {
      const pos = new THREE.Vector3((spec.x - 12) * S, (12 - spec.y) * S, spec.z);
      const len = pos.length();
      const dir = pos.clone().divideScalar(len);
      const start = HUB_R * 0.95;
      const end = len - (spec.solid ? SAT_R : RING_R) * 0.92;

      const spoke = new THREE.Mesh(new THREE.CylinderGeometry(0.014, 0.014, end - start, 8, 1, true), spokeMat);
      spoke.position.copy(dir).multiplyScalar((start + end) / 2);
      spoke.quaternion.setFromUnitVectors(up, dir);
      core.add(spoke);

      const group = new THREE.Group();
      group.position.copy(pos);
      let mat: ThreeNS.MeshPhysicalMaterial;
      let body: ThreeNS.Mesh;
      if (spec.solid) {
        mat = new THREE.MeshPhysicalMaterial({
          color: 0x8f0530,
          roughness: 0.2,
          metalness: 0.15,
          clearcoat: 1,
          clearcoatRoughness: 0.04,
          emissive: new THREE.Color(CRIMSON),
          emissiveIntensity: 0.22,
          envMapIntensity: 1.1,
          flatShading: true,
        });
        body = new THREE.Mesh(gemGeo, mat);
      } else {
        mat = new THREE.MeshPhysicalMaterial({
          color: 0xe9ecf2,
          roughness: 0.12,
          metalness: 1,
          emissive: new THREE.Color(0xff89a6),
          emissiveIntensity: 0.12,
          envMapIntensity: 1.6,
        });
        body = new THREE.Mesh(ringGeo, mat);
      }
      group.add(body);
      core.add(group);

      const pulse = new THREE.Mesh(new THREE.SphereGeometry(0.036, 12, 8), pulseMat);
      core.add(pulse);

      this.nodes.push({ spec, group, body, mat, pos, start, end, dir, pulse, hl: 0, sx: 0, sy: 0 });
    }
  }

  private entrance(): void {
    const canvas = this.canvasRef().nativeElement;
    if (this.reduced) {
      canvas.style.opacity = '1';
      return;
    }
    animate(canvas, { opacity: [0, 1], scale: [0.94, 1] }, { duration: 0.9, ease: EASE_OUT });
    animate(this.floorRef().nativeElement, { opacity: [0, 1] }, { duration: 1.2, ease: 'easeOut' });
  }

  private resize(): void {
    const THREE = this.THREE;
    if (!this.renderer || !this.camera || !THREE) return;
    const v = this.vitrine().nativeElement;
    const w = Math.max(v.clientWidth, 1);
    const h = Math.max(v.clientHeight, 1);
    this.w = w;
    this.h = h;
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    this.renderer.setSize(w, h, false);
    const aspect = w / h;
    this.camera.aspect = aspect;
    // Frame the whole glyph: by height on wide panels, by width on narrow ones.
    const visH = Math.max(EXTENT_H / 0.76, EXTENT_W / aspect / 0.92);
    this.dist = visH / (2 * Math.tan((this.camera.fov * Math.PI) / 360));
    this.camera.position.set(0, 0, this.dist);
    this.camera.lookAt(0, 0, 0);
    this.camera.updateProjectionMatrix();

    // The click target: one generous circle over the whole silhouette.
    const d = Math.min(((2 * CLICK_R) / visH) * h, w - 8);
    const btn = this.coreBtn().nativeElement;
    btn.style.width = `${d}px`;
    btn.style.height = `${d}px`;
    btn.style.left = `${(w - d) / 2}px`;
    btn.style.top = `${(h - d) / 2}px`;
    this.renderFrame();
  }

  // ---- loop --------------------------------------------------------------------------------------------------

  private onVisibility = (): void => {
    if (document.hidden) this.stopLoop();
    else this.startLoop();
  };

  private onMotionPref = (e: MediaQueryListEvent): void => {
    this.reduced = e.matches;
    if (this.reduced) {
      this.tx = this.ty = this.px = this.py = 0;
      this.stopLoop();
      this.renderFrame();
    } else {
      this.startLoop();
    }
  };

  private startLoop(): void {
    if (this.raf || !this.ready || document.hidden || !this.inView || this.reduced) return;
    this.lastTs = performance.now();
    const tick = (ts: number): void => {
      this.raf = 0;
      const dt = Math.min((ts - this.lastTs) / 1000, 0.1);
      this.lastTs = ts;
      this.clock += dt;
      const k = 1 - Math.exp(-dt * 5);
      this.px += (this.tx - this.px) * k;
      this.py += (this.ty - this.py) * k;
      this.renderFrame();
      if (!document.hidden && this.inView && !this.destroyed && !this.reduced) this.raf = requestAnimationFrame(tick);
    };
    this.raf = requestAnimationFrame(tick);
  }

  private stopLoop(): void {
    if (this.raf) cancelAnimationFrame(this.raf);
    this.raf = 0;
  }

  private renderFrame(): void {
    const { renderer, scene, camera, core, hub, shell, innerMat } = this;
    if (!renderer || !scene || !camera || !core || !hub || !innerMat || !this.THREE) return;
    const idle = this.reduced ? 0 : 1;
    const t = this.clock;
    const rush = this.rush;

    // Slow levitation and a sway wide enough to show depth but never edge-on to the flat glyph.
    core.position.y = Math.sin(t * 0.9) * 0.09 * idle;
    core.rotation.set(
      -0.1 + Math.sin(t * 0.21) * 0.05 * idle + this.py * 0.14 * idle,
      -0.32 + Math.sin(t * 0.27) * 0.5 * idle + this.px * 0.3 * idle,
      0,
      'YXZ',
    );
    const grow = 1 + this.hover * 0.03 + rush * rush * rush * 4;
    core.scale.setScalar(grow);
    core.position.z = Math.pow(rush, 2.2) * (this.dist - 3.4);

    hub.rotation.set(t * 0.13 * idle, t * 0.2 * idle, 0);
    if (shell) shell.rotation.set(-t * 0.07 * idle, -t * 0.11 * idle, 0);
    innerMat.emissiveIntensity = 2.4 + this.hover * 0.9 + Math.sin(t * 1.6) * 0.25 * idle + rush * 6;
    renderer.toneMappingExposure = 1 + rush * 0.9;

    for (const n of this.nodes) {
      const s = 1 + n.hl * 0.24;
      n.group.scale.setScalar(s);
      n.mat.emissiveIntensity = (n.spec.solid ? 0.22 : 0.12) + n.hl * (n.spec.solid ? 1.1 : 0.7);
      n.body.rotation.y = n.spec.solid ? t * 0.3 * idle : 0;
      const f = (t * 0.32 + NODES.indexOf(n.spec) * 0.17) % 1;
      n.pulse.position.copy(n.dir).multiplyScalar(n.start + (n.end - n.start) * f);
      n.pulse.scale.setScalar(idle * Math.sin(Math.PI * f));
    }

    const floor = this.floorRef().nativeElement;
    floor.style.transform = `translateX(-50%) scale(${(1 - Math.sin(t * 0.9) * 0.05 * idle).toFixed(3)})`;

    scene.updateMatrixWorld();
    renderer.render(scene, camera);
    this.syncHotspots();
  }

  /** Projects each satellite to the vitrine's pixel space and pins its hit area (and the open card) to it. */
  private syncHotspots(): void {
    const camera = this.camera;
    const v = this.tmp.v;
    if (!camera || !v) return;
    const btns = this.spotBtns();
    for (const n of this.nodes) {
      v.setFromMatrixPosition(n.group.matrixWorld).project(camera);
      n.sx = (v.x * 0.5 + 0.5) * this.w;
      n.sy = (-v.y * 0.5 + 0.5) * this.h;
      const btn = btns.find((b) => b.nativeElement.dataset['id'] === n.spec.id)?.nativeElement;
      if (btn) btn.style.transform = `translate3d(${(n.sx - 24).toFixed(1)}px, ${(n.sy - 24).toFixed(1)}px, 0)`;
    }
    this.positionCard();
  }

  private teardown(): void {
    this.stopLoop();
    document.removeEventListener('visibilitychange', this.onVisibility);
    this.motionQuery?.removeEventListener('change', this.onMotionPref);
    this.resizeObserver?.disconnect();
    this.intersection?.disconnect();
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
    this.nodes = [];
    this.ready = false;
  }
}
