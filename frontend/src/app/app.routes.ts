import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { CustomerProfilePageComponent } from './features/customers/customer-profile-page.component';
import { CustomerSearchPageComponent } from './features/customers/customer-search-page.component';
import { InteractionListPageComponent } from './features/interactions/interaction-list-page.component';
import { LoginPageComponent } from './features/login/login-page.component';

export const routes: Routes = [
  { path: 'login', component: LoginPageComponent },
  { path: 'customers', component: CustomerSearchPageComponent, canActivate: [authGuard] },
  { path: 'customers/:publicId', component: CustomerProfilePageComponent, canActivate: [authGuard] },
  { path: 'interactions', component: InteractionListPageComponent, canActivate: [authGuard] },
  { path: '', pathMatch: 'full', redirectTo: 'customers' },
  { path: '**', redirectTo: 'customers' }
];
