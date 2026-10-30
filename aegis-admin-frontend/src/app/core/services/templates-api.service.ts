import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { RUNTIME_CONFIG } from '../config/runtime-config';
import {
  CreateTemplateRequest,
  SetTemplateStatusRequest,
  TemplateResponse,
  UpdateTemplateRequest,
} from '../models/template.models';

@Injectable({ providedIn: 'root' })
export class TemplatesApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(RUNTIME_CONFIG).apiBaseUrl.replace(/\/+$/, '');
  private readonly templatesUrl = `${this.baseUrl}/api/v1/templates`;

  list(): Observable<TemplateResponse[]> {
    return this.http.get<TemplateResponse[]>(this.templatesUrl);
  }

  get(id: string): Observable<TemplateResponse> {
    return this.http.get<TemplateResponse>(`${this.templatesUrl}/${encodeURIComponent(id)}`);
  }

  create(body: CreateTemplateRequest): Observable<TemplateResponse> {
    return this.http.post<TemplateResponse>(this.templatesUrl, body);
  }

  update(id: string, body: UpdateTemplateRequest): Observable<TemplateResponse> {
    return this.http.put<TemplateResponse>(`${this.templatesUrl}/${encodeURIComponent(id)}`, body);
  }

  setStatus(id: string, active: boolean): Observable<TemplateResponse> {
    const body: SetTemplateStatusRequest = { active };
    return this.http.patch<TemplateResponse>(
      `${this.templatesUrl}/${encodeURIComponent(id)}/status`,
      body,
    );
  }
}
