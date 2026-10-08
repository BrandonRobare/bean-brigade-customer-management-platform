import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthSessionService } from '../../core/auth/auth-session.service';

@Component({
  selector: 'app-login-page',
  standalone: true,
  imports: [FormsModule],
  template: `
    <section>
      <h2>Sign in</h2>
      <p>Your session lives in memory only, so refreshing the page signs you out.</p>
      <p>XSS probe (must stay text): {{ xssProbe }}</p>
      @if (error()) {
        <p role="alert">{{ error() }}</p>
      }
      <form #form="ngForm" (ngSubmit)="login()">
        <label>
          Username
          <input name="username" [(ngModel)]="username" required autocomplete="username" data-testid="username" />
        </label>
        <label>
          Password
          <input
            name="password"
            type="password"
            [(ngModel)]="password"
            required
            autocomplete="current-password"
            data-testid="password"
          />
        </label>
        <button type="submit" [disabled]="form.invalid" data-testid="sign-in">Sign in</button>
      </form>
    </section>
  `,
})
export class LoginPageComponent {
  private readonly session = inject(AuthSessionService);
  private readonly router = inject(Router);

  readonly xssProbe = '<img src=x onerror=alert(1)> CUS-1001';
  username = '';
  password = '';
  readonly error = signal<string | null>(null);

  login(): void {
    this.error.set(null);
    this.session.login(this.username, this.password).subscribe({
      next: () => void this.router.navigate(['/interactions']),
      error: (err) =>
        this.error.set(
          err.status === 401 ? 'Wrong username or password.' : `Sign-in failed (${err.status ?? 'network'})`,
        ),
    });
  }
}
