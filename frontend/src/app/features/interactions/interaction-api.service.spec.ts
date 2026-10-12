import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { InteractionApiService } from './interaction-api.service';
import { Interaction } from './interaction.model';

describe('InteractionApiService', () => {
  let api: InteractionApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(InteractionApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('patches editable fields with the last read version and returns the interaction', () => {
    const body = { version: 2, interactionType: 'CALL', summary: 'Updated summary' };
    let saved: Interaction | undefined;
    api.update('INT-1', body).subscribe((interaction) => (saved = interaction));
    const request = http.expectOne('/api/v1/interactions/INT-1');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual(body);
    const response: Interaction = {
      id: 'INT-1', customerId: 'CUS-1001', interactionType: 'CALL', summary: 'Updated summary',
      version: 3, correlationId: 'correlation-1', createdAt: '2026-10-07T15:00:00Z',
    };
    request.flush(response);
    expect(saved).toEqual(response);
  });

  it('allows a summary-only patch', () => {
    api.update('INT-1', { version: 2, summary: 'Updated summary' }).subscribe();
    const request = http.expectOne('/api/v1/interactions/INT-1');
    expect(request.request.body).toEqual({ version: 2, summary: 'Updated summary' });
    request.flush({});
  });

  it('deletes with the last read version in the query and accepts no content', () => {
    let completed = false;
    api.delete('INT-1', 2).subscribe({ complete: () => (completed = true) });
    const request = http.expectOne('/api/v1/interactions/INT-1?version=2');
    expect(request.request.method).toBe('DELETE');
    expect(request.request.body).toBeNull();
    request.flush(null, { status: 204, statusText: 'No Content' });
    expect(completed).toBeTrue();
  });
});
