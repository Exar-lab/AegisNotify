import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Subject, catchError, map, of, startWith, switchMap } from 'rxjs';
import { TemplateResponse } from '../../../core/models/template.models';
import { TemplatesApiService } from '../../../core/services/templates-api.service';

type ListState = { kind: 'loading' | 'error' } | { kind: 'ready'; data: TemplateResponse[] };

/**
 * Templates view: lists notification templates and toggles their active status.
 */
@Component({
  selector: 'app-templates-page',
  standalone: true,
  imports: [DatePipe, RouterLink],
  templateUrl: './templates.page.html',
  styleUrl: './templates.page.scss',
})
export class TemplatesPage {
  private readonly api = inject(TemplatesApiService);
  private readonly requests = new Subject<void>();

  readonly state = toSignal(
    this.requests.pipe(
      startWith(undefined),
      switchMap(() =>
        this.api.list().pipe(
          map((data): ListState => ({ kind: 'ready', data })),
          catchError(() => of<ListState>({ kind: 'error' })),
          startWith<ListState>({ kind: 'loading' }),
        ),
      ),
    ),
    { initialValue: { kind: 'loading' } as ListState },
  );
  readonly templates = computed(() => {
    const state = this.state();
    return state.kind === 'ready' ? state.data : [];
  });

  readonly actionError = signal('');
  readonly busyId = signal<string | null>(null);

  load(): void {
    this.requests.next();
  }

  toggleStatus(template: TemplateResponse): void {
    this.busyId.set(template.id);
    this.actionError.set('');
    this.api.setStatus(template.id, !template.active).subscribe({
      next: () => {
        this.busyId.set(null);
        this.load();
      },
      error: () => {
        this.busyId.set(null);
        this.actionError.set(`Couldn't update ${template.name}. Please try again.`);
      },
    });
  }
}
