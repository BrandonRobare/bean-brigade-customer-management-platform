import { HttpErrorResponse } from '@angular/common/http';
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
      <form (ngSubmit)="login()">
        <label>
          Username
          <input name="username" [(ngModel)]="username" autocomplete="username" required />
        </label>
        <label>
          Password
          <input
            name="password"
            type="password"
            [(ngModel)]="password"
            autocomplete="current-password"
            required
          />
        </label>
        <button type="submit" [disabled]="submitting() || !username || !password">Sign in</button>
        @if (error()) {
          <p role="alert">{{ error() }}</p>
        }
      </form>
    </section>
  `,
  styles: `
    .login-card {
      max-width: 420px;
      margin: 3rem auto;
      padding: 2rem;
      background: var(--color-surface);
      border: 1px solid var(--color-border);
      border-top: 4px solid var(--color-blue);
      border-radius: 12px;
      box-shadow: 0 4px 16px rgba(10, 35, 66, 0.1);
    }
  .login-card form { max-width: none; }
  .hint { margin: 0 0 1.25rem; color: var(--color-muted); }
  `
})
export class LoginPageComponent {
  private readonly session = inject(AuthSessionService);
  private readonly router = inject(Router);

  username = '';
  password = '';
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);

  login(): void {
    this.error.set(null);
    this.submitting.set(true);
    this.session.login(this.username, this.password).subscribe({
      next: () => {
        this.password = '';
        void this.router.navigate(['/interactions']);
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.error.set(
          err.status === 401 || err.status === 400
            ? 'Incorrect username or password.'
            : 'Unable to sign in right now. Please try again.',
        );
      },
    });
  }
}
