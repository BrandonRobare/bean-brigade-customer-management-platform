import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

interface LoginResponse {
  accessToken: string;
}

/** Demo-only. Matches Boot `DemoBearerFilter`. Not a production secret. */
export const LAB_DEMO_TOKEN = 'lab-demo-token';

@Injectable({ providedIn: 'root' })
export class AuthSessionService {
  private readonly http = inject(HttpClient);
  readonly accessToken = signal<string | null>(null);

  login(username: string, password: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>('/api/v1/auth/login', { username, password })
      .pipe(tap((res) => this.accessToken.set(res.accessToken)));
  }

  logout(): void {
    this.accessToken.set(null);
  }
}

