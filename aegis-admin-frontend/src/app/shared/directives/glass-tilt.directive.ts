import { Directive, ElementRef, NgZone, OnDestroy, OnInit, inject, input } from '@angular/core';
import { animate, motionValue, type AnimationPlaybackControls, type MotionValue } from 'motion';

/** Default maximum rotation applied on either axis, in degrees. */
const DEFAULT_MAX_TILT_DEG = 6;

/** Soft spring shared by every animated channel — settles without overshoot jitter. */
const SPRING = { type: 'spring', stiffness: 220, damping: 22, mass: 0.6 } as const;

/**
 * Cursor-driven 3D tilt + radial glow for card-shaped surfaces ("Glass Depth").
 *
 * Writes four CSS custom properties on the host, consumed by `.glass-card`:
 * `--tilt-x` / `--tilt-y` (deg), `--glow-x` / `--glow-y` (%) and `--glow-opacity`.
 * Values are spring-smoothed through `motion` rather than snapped.
 *
 * Optionally pass a smaller max angle for large surfaces: `appGlassTilt="2"`.
 *
 * No-ops entirely on coarse pointers (touch) and under `prefers-reduced-motion: reduce`,
 * leaving the plain CSS hover elevation in place.
 */
@Directive({
  selector: '[appGlassTilt]',
  standalone: true,
})
export class GlassTiltDirective implements OnInit, OnDestroy {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly zone = inject(NgZone);

  /** Max tilt in degrees; a bare `appGlassTilt` attribute uses the default. */
  readonly appGlassTilt = input(DEFAULT_MAX_TILT_DEG, {
    transform: (value: number | string | null | undefined): number => {
      const parsed = Number(value);
      return value === '' || value == null || !Number.isFinite(parsed) ? DEFAULT_MAX_TILT_DEG : parsed;
    },
  });

  private readonly channels: Record<string, { value: MotionValue<number>; unit: string }> = {
    '--tilt-x': { value: motionValue(0), unit: 'deg' },
    '--tilt-y': { value: motionValue(0), unit: 'deg' },
    '--glow-x': { value: motionValue(50), unit: '%' },
    '--glow-y': { value: motionValue(50), unit: '%' },
    '--glow-opacity': { value: motionValue(0), unit: '' },
  };

  private readonly running = new Map<string, AnimationPlaybackControls>();
  private readonly unsubscribers: Array<() => void> = [];
  private enabled = false;

  private readonly onPointerMove = (event: PointerEvent): void => {
    if (event.pointerType === 'touch') return;
    const rect = this.host.nativeElement.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) return;

    // Normalised cursor position within the card: 0..1 on each axis.
    const px = Math.min(Math.max((event.clientX - rect.left) / rect.width, 0), 1);
    const py = Math.min(Math.max((event.clientY - rect.top) / rect.height, 0), 1);

    // Cursor at the top edge tips the top away from the viewer (positive rotateX);
    // cursor at the right edge tips the right side away (negative rotateY).
    const maxTilt = this.appGlassTilt();
    this.animateTo('--tilt-x', (0.5 - py) * 2 * maxTilt);
    this.animateTo('--tilt-y', (px - 0.5) * 2 * maxTilt);
    this.animateTo('--glow-x', px * 100);
    this.animateTo('--glow-y', py * 100);
    this.animateTo('--glow-opacity', 1);
  };

  private readonly onPointerLeave = (): void => {
    this.animateTo('--tilt-x', 0);
    this.animateTo('--tilt-y', 0);
    this.animateTo('--glow-opacity', 0);
  };

  ngOnInit(): void {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return;
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const coarsePointer = window.matchMedia('(pointer: coarse)').matches;
    if (reducedMotion || coarsePointer) return;

    this.enabled = true;
    const el = this.host.nativeElement;
    el.classList.add('glass-tilt-active');

    for (const [prop, { value, unit }] of Object.entries(this.channels)) {
      el.style.setProperty(prop, `${value.get()}${unit}`);
      this.unsubscribers.push(value.on('change', (v) => el.style.setProperty(prop, `${v}${unit}`)));
    }

    // Pointer events fire at high frequency — keep them out of change detection.
    this.zone.runOutsideAngular(() => {
      el.addEventListener('pointermove', this.onPointerMove, { passive: true });
      el.addEventListener('pointerleave', this.onPointerLeave, { passive: true });
    });
  }

  ngOnDestroy(): void {
    if (!this.enabled) return;
    const el = this.host.nativeElement;
    el.removeEventListener('pointermove', this.onPointerMove);
    el.removeEventListener('pointerleave', this.onPointerLeave);
    this.running.forEach((controls) => controls.stop());
    this.running.clear();
    this.unsubscribers.forEach((unsubscribe) => unsubscribe());
    this.unsubscribers.length = 0;
    Object.values(this.channels).forEach(({ value }) => value.destroy());
  }

  private animateTo(prop: string, target: number): void {
    this.running.get(prop)?.stop();
    this.running.set(prop, animate(this.channels[prop].value, target, SPRING));
  }
}
