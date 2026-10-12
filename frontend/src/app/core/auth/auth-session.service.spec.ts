import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthSessionService } from './auth-session.service';

describe('AuthSessionService', () => {
  let session: AuthSessionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    session = TestBed.inject(AuthSessionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('keeps the token from a good login', () => {
    session.login('agent1', 'right').subscribe();
    const req = http.expectOne('/api/v1/auth/login');
    expect(req.request.body).toEqual({ username: 'agent1', password: 'right' });
    req.flush({ accessToken: 'signed.jwt.value', tokenType: 'Bearer', expiresIn: 1800 });
    expect(session.accessToken()).toBe('signed.jwt.value');
  });

  it('creates no session on 401', () => {
    session.accessToken.set('old.jwt.value');
    session.login('agent1', 'wrong').subscribe({ error: () => undefined });
    http.expectOne('/api/v1/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(session.accessToken()).toBeNull();
  });

  it('reads roles from the token and clears them on logout', () => {
    session.accessToken.set(`header.${btoa(JSON.stringify({ roles: ['ADMIN'] }))}.signature`);
    expect(session.roles()).toEqual(['ADMIN']);
    session.logout();
    expect(session.roles()).toEqual([]);
  });

  it('accepts base64url claims', () => {
    session.accessToken.set('header.eyJyb2xlcyI6WyJBR0VOVCJdLCJzdWIiOiI_Pz8ifQ.signature');
    expect(session.roles()).toEqual(['AGENT']);
  });

  for (const token of ['invalid', 'header.invalid.signature', `header.${btoa(JSON.stringify({ roles: 'ADMIN' }))}.signature`]) {
    it(`grants no UI roles for malformed token ${token}`, () => {
      session.accessToken.set(token);
      expect(session.roles()).toEqual([]);
    });
  }
});
