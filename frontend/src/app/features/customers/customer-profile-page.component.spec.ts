import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { CustomerProfilePageComponent } from './customer-profile-page.component';

describe('CustomerProfilePageComponent', () => {
  let fixture: ComponentFixture<CustomerProfilePageComponent>;
  let http: HttpTestingController;
  let paramMap: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  beforeEach(async () => {
    paramMap = new BehaviorSubject(convertToParamMap({ publicId: 'CUS-1001' }));
    await TestBed.configureTestingModule({
      imports: [CustomerProfilePageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { paramMap, snapshot: { queryParamMap: convertToParamMap({}) } },
        },
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CustomerProfilePageComponent);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('loads the customer and renders interactions in the order returned', () => {
    http.expectOne('/api/v1/customers/CUS-1001').flush({
      publicId: 'CUS-1001',
      fullName: 'Amina Khan',
      status: 'ACTIVE',
      createdAt: '2026-10-06T20:00:00Z',
      version: 0,
    });
    http.expectOne('/api/v1/interactions?customerId=CUS-1001').flush([
      {
        id: 'INT-2',
        customerId: 'CUS-1001',
        interactionType: 'CALL',
        summary: 'Newest interaction',
        correlationId: 'correlation-2',
        createdAt: '2026-10-08T15:00:00Z',
        version: 0,
      },
      {
        id: 'INT-1',
        customerId: 'CUS-1001',
        interactionType: 'EMAIL',
        summary: 'Older interaction',
        correlationId: 'correlation-1',
        createdAt: '2026-10-07T15:00:00Z',
        version: 0,
      },
    ]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="customer-profile-name"]').textContent)
      .toContain('Amina Khan');
    expect(fixture.nativeElement.querySelector('[data-testid="customer-profile-id"]').textContent)
      .toContain('CUS-1001');
    expect(fixture.nativeElement.querySelector('[data-testid="customer-profile-status"]').textContent)
      .toContain('ACTIVE');
    const items = fixture.nativeElement.querySelectorAll('[data-testid="interaction-timeline-item"]');
    expect(items.length).toBe(2);
    expect(items[0].textContent).toContain('CALL');
    expect(items[0].textContent).toContain('Newest interaction');
    expect(items[0].textContent).toContain('Oct 8, 2026');
    expect(items[1].textContent).toContain('EMAIL');
    expect(items[1].textContent).toContain('Older interaction');
    expect(items[1].textContent).toContain('Oct 7, 2026');
  });

  it('shows an empty state when the customer has no interactions', () => {
    flushCustomer();
    http.expectOne('/api/v1/interactions?customerId=CUS-1001').flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="interaction-timeline-empty"]')?.textContent)
      .toContain('No interactions yet.');
  });

  it('shows not found and does not request interactions when the customer is unknown', () => {
    http
      .expectOne('/api/v1/customers/CUS-1001')
      .flush(null, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="customer-profile-not-found"]')?.textContent)
      .toContain('Customer CUS-1001 was not found.');
    http.expectNone('/api/v1/interactions?customerId=CUS-1001');
  });

  it('shows an invalid ID without retrying or requesting interactions', () => {
    http.expectOne('/api/v1/customers/CUS-1001').flush(null, { status: 404, statusText: 'Not Found' });
    paramMap.next(convertToParamMap({ publicId: 'abc' }));
    http.expectOne('/api/v1/customers/abc').flush(
      { type: 'about:blank', title: 'Bad Request', status: 400, detail: 'Invalid customer ID' },
      { status: 400, statusText: 'Bad Request', headers: { 'Content-Type': 'application/problem+json' } },
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="customer-profile-invalid-id"]')?.textContent)
      .toContain('Customer ID abc is not valid.');
    expect(fixture.nativeElement.querySelector('[data-testid="customer-profile-retry"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="customer-profile-error"]')).toBeNull();
    http.expectNone('/api/v1/interactions?customerId=abc');
  });

  it('keeps retry available for a network error', () => {
    http.expectOne('/api/v1/customers/CUS-1001').error(new ProgressEvent('error'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="customer-profile-error"]')?.textContent)
      .toContain('Customer profile failed to load.');
    fixture.nativeElement.querySelector('[data-testid="customer-profile-retry"]').click();
    flushCustomer();
    http.expectOne('/api/v1/interactions?customerId=CUS-1001').flush([]);
  });

  it('shows a customer error and retries the profile request', () => {
    http
      .expectOne('/api/v1/customers/CUS-1001')
      .flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="customer-profile-error"]')).not.toBeNull();
    fixture.nativeElement.querySelector('[data-testid="customer-profile-retry"]').click();
    http.expectOne('/api/v1/customers/CUS-1001').flush({
      publicId: 'CUS-1001',
      fullName: 'Amina Khan',
      status: 'ACTIVE',
      createdAt: '2026-10-06T20:00:00Z',
      version: 0,
    });
    http.expectOne('/api/v1/interactions?customerId=CUS-1001').flush([]);
  });

  it('shows an interaction error and retries only the interaction request', () => {
    flushCustomer();
    http
      .expectOne('/api/v1/interactions?customerId=CUS-1001')
      .flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="interaction-timeline-error"]')).not.toBeNull();
    fixture.nativeElement.querySelector('[data-testid="interaction-timeline-retry"]').click();
    http.expectNone('/api/v1/customers/CUS-1001');
    http.expectOne('/api/v1/interactions?customerId=CUS-1001').flush([]);
  });

  it('refetches interactions after a save and renders the saved interaction first', async () => {
    flushCustomer();
    http.expectOne('/api/v1/interactions?customerId=CUS-1001').flush([
      {
        id: 'INT-1',
        customerId: 'CUS-1001',
        interactionType: 'EMAIL',
        summary: 'Older interaction',
        correlationId: 'correlation-1',
        createdAt: '2026-10-07T15:00:00Z',
        version: 0,
      },
    ]);
    fixture.detectChanges();
    await fixture.whenStable();

    const summary: HTMLTextAreaElement = fixture.nativeElement.querySelector(
      '[data-testid="interaction-summary"]',
    );
    summary.value = 'New interaction';
    summary.dispatchEvent(new Event('input'));
    fixture.nativeElement
      .querySelector('[data-testid="interaction-form"]')
      .dispatchEvent(new Event('submit'));
    http.expectOne('/api/v1/interactions').flush(
      {
        id: 'INT-2',
        customerId: 'CUS-1001',
        interactionType: 'NOTE',
        summary: 'New interaction',
        correlationId: 'correlation-2',
        createdAt: '2026-10-08T15:00:00Z',
        version: 0,
      },
      { status: 201, statusText: 'Created' },
    );
    http.expectOne('/api/v1/interactions?customerId=CUS-1001').flush([
      {
        id: 'INT-2',
        customerId: 'CUS-1001',
        interactionType: 'NOTE',
        summary: 'New interaction',
        correlationId: 'correlation-2',
        createdAt: '2026-10-08T15:00:00Z',
        version: 0,
      },
      {
        id: 'INT-1',
        customerId: 'CUS-1001',
        interactionType: 'EMAIL',
        summary: 'Older interaction',
        correlationId: 'correlation-1',
        createdAt: '2026-10-07T15:00:00Z',
        version: 0,
      },
    ]);
    fixture.detectChanges();

    const items = fixture.nativeElement.querySelectorAll('[data-testid="interaction-timeline-item"]');
    expect(items.length).toBe(2);
    expect(items[0].textContent).toContain('New interaction');
    expect(items[1].textContent).toContain('Older interaction');
  });

  it('cancels the previous interactions request when the route changes', () => {
    flushCustomer();
    const firstInteractionRequest = http.expectOne('/api/v1/interactions?customerId=CUS-1001');

    paramMap.next(convertToParamMap({ publicId: 'CUS-1002' }));

    expect(firstInteractionRequest.cancelled).toBeTrue();
    http.expectOne('/api/v1/customers/CUS-1002').flush({
      publicId: 'CUS-1002',
      fullName: 'Jon Bell',
      status: 'PROSPECT',
      createdAt: '2026-10-07T20:00:00Z',
      version: 0,
    });
    http.expectOne('/api/v1/interactions?customerId=CUS-1002').flush([]);
  });

  it('renders an HTML payload in a summary as text', () => {
    flushCustomer();
    http.expectOne('/api/v1/interactions?customerId=CUS-1001').flush([
      {
        id: 'INT-1',
        customerId: 'CUS-1001',
        interactionType: 'NOTE',
        summary: '<img src=x onerror=alert(1)>',
        correlationId: 'correlation-1',
        createdAt: '2026-10-07T15:00:00Z',
        version: 0,
      },
    ]);
    fixture.detectChanges();

    const item = fixture.nativeElement.querySelector('[data-testid="interaction-timeline-item"]');
    expect(item.textContent).toContain('<img src=x onerror=alert(1)>');
    expect(item.querySelector('img')).toBeNull();
  });

  it('edits as AGENT and refetches the timeline after success', async () => {
    await loadTimeline('AGENT');
    element('interaction-edit').click();
    fixture.detectChanges();
    await fixture.whenStable();
    setValue('interaction-edit-summary', '  Updated summary  ');
    setValue('interaction-edit-type', 'EMAIL', 'change');
    element('interaction-edit-form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    const request = http.expectOne('/api/v1/interactions/INT-1');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ version: 3, interactionType: 'EMAIL', summary: 'Updated summary' });
    expect((element('interaction-edit-save') as HTMLButtonElement).disabled).toBeTrue();
    expect(element('interaction-edit-save').textContent).toContain('Saving…');
    request.flush({ ...interaction, interactionType: 'EMAIL', summary: 'Updated summary', version: 4 });
    const refresh = http.expectOne('/api/v1/interactions?customerId=CUS-1001');
    expect(refresh.request.method).toBe('GET');
    refresh.flush([{ ...interaction, interactionType: 'EMAIL', summary: 'Updated summary', version: 4 }]);
    fixture.detectChanges();
    expect(element('interaction-timeline-item').textContent).toContain('Updated summary');
    expect(fixture.nativeElement.querySelector('[data-testid="interaction-edit-form"]')).toBeNull();
  });

  it('keeps edit values on conflict and reloads only when asked', async () => {
    await loadTimeline('AGENT');
    element('interaction-edit').click();
    fixture.detectChanges();
    await fixture.whenStable();
    setValue('interaction-edit-summary', 'My draft');
    setValue('interaction-edit-type', 'MEETING', 'change');
    element('interaction-edit-form').dispatchEvent(new Event('submit'));
    http.expectOne('/api/v1/interactions/INT-1').flush(null, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect((element('interaction-edit-summary') as HTMLTextAreaElement).value).toBe('My draft');
    expect((element('interaction-edit-type') as HTMLSelectElement).value).toBe('MEETING');
    expect(element('interaction-mutation-error').textContent).toContain('changed');
    expect(element('interaction-mutation-error').getAttribute('role')).toBe('alert');
    expect((element('interaction-edit-save') as HTMLButtonElement).disabled).toBeTrue();
    http.expectNone('/api/v1/interactions/INT-1');
    http.expectNone('/api/v1/interactions?customerId=CUS-1001');
    element('interaction-reload').click();
    http.expectOne('/api/v1/interactions?customerId=CUS-1001').flush([{ ...interaction, version: 4 }]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="interaction-mutation-error"]')).toBeNull();
    expect((element('interaction-edit') as HTMLButtonElement).disabled).toBeFalse();
  });

  it('shows delete only for ADMIN and sends no request when confirmation is cancelled', async () => {
    await loadTimeline('AGENT');
    expect(fixture.nativeElement.querySelector('[data-testid="interaction-delete"]')).toBeNull();
    setRole('ADMIN');
    fixture.detectChanges();
    const confirm = spyOn(window, 'confirm').and.returnValue(false);
    element('interaction-delete').click();
    expect(confirm).toHaveBeenCalled();
    http.expectNone('/api/v1/interactions/INT-1?version=3');
    expect(element('interaction-timeline-item').textContent).toContain('Original summary');
  });

  it('deletes the selected interaction with its version and refetches after 204', async () => {
    await loadTimeline('ADMIN');
    spyOn(window, 'confirm').and.returnValue(true);
    element('interaction-delete').click();
    fixture.detectChanges();
    const request = http.expectOne('/api/v1/interactions/INT-1?version=3');
    expect(request.request.method).toBe('DELETE');
    expect((element('interaction-delete') as HTMLButtonElement).disabled).toBeTrue();
    expect(element('interaction-delete').textContent).toContain('Deleting…');
    http.expectNone('/api/v1/interactions?customerId=CUS-1001');
    request.flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne('/api/v1/interactions?customerId=CUS-1001').flush([]);
    fixture.detectChanges();
    expect(element('interaction-timeline-empty').textContent).toContain('No interactions yet.');
  });

  it('cancels editing without a request', async () => {
    await loadTimeline('ADMIN');
    element('interaction-edit').click();
    fixture.detectChanges();
    element('interaction-edit-cancel').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="interaction-edit-form"]')).toBeNull();
    http.expectNone('/api/v1/interactions/INT-1');
  });

  for (const summary of ['   ', 'x'.repeat(1025)]) {
    it(`rejects ${summary.length === 3 ? 'blank' : 'too long'} edit summaries without a request`, async () => {
      await loadTimeline('AGENT');
      element('interaction-edit').click();
      fixture.detectChanges();
      await fixture.whenStable();
      setValue('interaction-edit-summary', summary);
      element('interaction-edit-form').dispatchEvent(new Event('submit'));
      fixture.detectChanges();
      expect(element('interaction-edit-summary-error').getAttribute('role')).toBe('alert');
      http.expectNone('/api/v1/interactions/INT-1');
    });
  }

  for (const status of [400, 401, 403, 404, 500]) {
    it(`shows an edit error for ${status} and keeps the draft`, async () => {
      await loadTimeline('AGENT');
      element('interaction-edit').click();
      fixture.detectChanges();
      await fixture.whenStable();
      setValue('interaction-edit-summary', 'Unsaved draft');
      element('interaction-edit-form').dispatchEvent(new Event('submit'));
      http.expectOne('/api/v1/interactions/INT-1').flush(null, { status, statusText: 'Error' });
      fixture.detectChanges();
      expect(element('interaction-mutation-error').getAttribute('role')).toBe('alert');
      expect((element('interaction-edit-summary') as HTMLTextAreaElement).value).toBe('Unsaved draft');
      expect(element('interaction-mutation-error').textContent).toContain(
        status === 401 ? 'Sign in' : status === 403 ? 'permission' : status === 404 ? 'no longer exists' : 'not saved',
      );
      http.expectNone('/api/v1/interactions?customerId=CUS-1001');
    });
  }

  it('shows a delete conflict without retrying or refetching', async () => {
    await loadTimeline('ADMIN');
    spyOn(window, 'confirm').and.returnValue(true);
    element('interaction-delete').click();
    http.expectOne('/api/v1/interactions/INT-1?version=3').flush(null, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect(element('interaction-mutation-error').textContent).toContain('changed');
    expect((element('interaction-delete') as HTMLButtonElement).disabled).toBeTrue();
    http.expectNone('/api/v1/interactions/INT-1?version=3');
    http.expectNone('/api/v1/interactions?customerId=CUS-1001');
  });

  for (const status of [401, 403, 404, 500]) {
    it(`shows a delete error for ${status} without changing the timeline`, async () => {
      await loadTimeline('ADMIN');
      spyOn(window, 'confirm').and.returnValue(true);
      element('interaction-delete').click();
      http.expectOne('/api/v1/interactions/INT-1?version=3').flush(null, { status, statusText: 'Error' });
      fixture.detectChanges();
      expect(element('interaction-mutation-error').textContent).toContain(
        status === 401 ? 'Sign in' : status === 403 ? 'permission' : status === 404 ? 'no longer exists' : 'not deleted',
      );
      expect(element('interaction-timeline-item').textContent).toContain('Original summary');
      http.expectNone('/api/v1/interactions?customerId=CUS-1001');
    });
  }

  it('cancels a pending edit request when destroyed', async () => {
    await loadTimeline('AGENT');
    element('interaction-edit').click();
    fixture.detectChanges();
    await fixture.whenStable();
    element('interaction-edit-form').dispatchEvent(new Event('submit'));
    const request = http.expectOne('/api/v1/interactions/INT-1');
    fixture.destroy();
    expect(request.cancelled).toBeTrue();
  });

  it('hides mutation controls without an authorized role', async () => {
    await loadTimeline('VIEWER');
    expect(fixture.nativeElement.querySelector('[data-testid="interaction-edit"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-testid="interaction-delete"]')).toBeNull();
  });

  const interaction = {
    id: 'INT-1', customerId: 'CUS-1001', interactionType: 'NOTE', summary: 'Original summary',
    correlationId: 'correlation-1', createdAt: '2026-10-07T15:00:00Z', version: 3,
  };

  function setRole(role: string): void {
    TestBed.inject(AuthSessionService).accessToken.set(`header.${btoa(JSON.stringify({ roles: [role] }))}.signature`);
  }

  async function loadTimeline(role: string): Promise<void> {
    setRole(role);
    flushCustomer();
    http.expectOne('/api/v1/interactions?customerId=CUS-1001').flush([interaction]);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function element(id: string): HTMLElement {
    return fixture.nativeElement.querySelector(`[data-testid="${id}"]`);
  }

  function setValue(id: string, value: string, event = 'input'): void {
    (element(id) as HTMLInputElement).value = value;
    element(id).dispatchEvent(new Event(event));
  }

  function flushCustomer(): void {
    http.expectOne('/api/v1/customers/CUS-1001').flush({
      publicId: 'CUS-1001',
      fullName: 'Amina Khan',
      status: 'ACTIVE',
      createdAt: '2026-10-06T20:00:00Z',
      version: 0,
    });
  }
});
