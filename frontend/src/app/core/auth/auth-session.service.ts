import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

@Injectable({ providedIn: 'root' })
export class AuthSessionService {
  private readonly http = inject(HttpClient);
  readonly accessToken = signal<string | null>(null);

  login(username: string, password: string): Observable<LoginResponse> {
    this.logout();
    return this.http
      .post<LoginResponse>(`${environment.apiBaseUrl}/api/v1/auth/login`, { username, password })
      .pipe(tap((response) => this.accessToken.set(response.accessToken)));
  }

  logout(): void {
    this.accessToken.set(null);
  }
}
