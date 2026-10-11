import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Customer } from './customer.model';

@Injectable({ providedIn: 'root' })
export class CustomerApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/api/v1/customers`;

  search(query: string): Observable<Customer[]> {
    const params = new HttpParams().set('query', query);
    return this.http.get<Customer[]>(this.base, { params });
  }
}
