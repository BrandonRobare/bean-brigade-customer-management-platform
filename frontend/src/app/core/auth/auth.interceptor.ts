import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthSessionService } from './auth-session.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(AuthSessionService).accessToken();
  // TODO: if token is present and this is a relative API request,
  // clone the request with Authorization: Bearer <token>
  if (!token || !req.url.startsWith('/api/')) {
    return next(req);
  }
  return next(req);
};
