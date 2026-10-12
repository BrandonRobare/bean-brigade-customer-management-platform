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

  get(publicId: string): Observable<Customer> {
    return this.http.get<Customer>(`${this.base}/${encodeURIComponent(publicId)}`);
  }

  create(fullName: string): Observable<Customer> {
    return this.http.post<Customer>(this.base, { fullName });
  }

  update(publicId: string, fullName: string, version: number): Observable<Customer> {
    return this.http.put<Customer>(`${this.base}/${encodeURIComponent(publicId)}`, { fullName, version });
  }

  changeStatus(publicId: string, status: Customer['status'], version: number): Observable<Customer> {
    return this.http.patch<Customer>(`${this.base}/${encodeURIComponent(publicId)}/status`, { status, version });
  }

  delete(publicId: string, version: number): Observable<void> {
    const params = new HttpParams().set('version', version);
    return this.http.delete<void>(`${this.base}/${encodeURIComponent(publicId)}`, { params });
  }
}
