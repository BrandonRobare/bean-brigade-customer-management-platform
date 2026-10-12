import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreateInteractionRequest, Interaction, UpdateInteractionRequest } from './interaction.model';

@Injectable({ providedIn: 'root' })
export class InteractionApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/api/v1/interactions`;

  list(customerId: string): Observable<Interaction[]> {
    // DONE: GET this.base with query param customerId via HttpClient
    const params = new HttpParams().set('customerId', customerId);
    return this.http.get<Interaction[]>(this.base, { params });
  }

  create(body: CreateInteractionRequest): Observable<Interaction> {
    // DONE: POST this.base with JSON body
    return this.http.post<Interaction>(this.base, body);
  }

  update(id: string, body: UpdateInteractionRequest): Observable<Interaction> {
    return this.http.patch<Interaction>(`${this.base}/${id}`, body);
  }

  delete(id: string, version: number): Observable<void> {
    const params = new HttpParams().set('version', version);
    return this.http.delete<void>(`${this.base}/${id}`, { params });
  }
}
