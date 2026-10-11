import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
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
        { provide: ActivatedRoute, useValue: { paramMap } },
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
    });
    http.expectOne('/api/v1/interactions?customerId=CUS-1001').flush([
      {
        id: 'INT-2',
        customerId: 'CUS-1001',
        interactionType: 'CALL',
        summary: 'Newest interaction',
        correlationId: 'correlation-2',
        createdAt: '2026-10-08T15:00:00Z',
      },
      {
        id: 'INT-1',
        customerId: 'CUS-1001',
        interactionType: 'EMAIL',
        summary: 'Older interaction',
        correlationId: 'correlation-1',
        createdAt: '2026-10-07T15:00:00Z',
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
      },
      {
        id: 'INT-1',
        customerId: 'CUS-1001',
        interactionType: 'EMAIL',
        summary: 'Older interaction',
        correlationId: 'correlation-1',
        createdAt: '2026-10-07T15:00:00Z',
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
      },
    ]);
    fixture.detectChanges();

    const item = fixture.nativeElement.querySelector('[data-testid="interaction-timeline-item"]');
    expect(item.textContent).toContain('<img src=x onerror=alert(1)>');
    expect(item.querySelector('img')).toBeNull();
  });

  function flushCustomer(): void {
    http.expectOne('/api/v1/customers/CUS-1001').flush({
      publicId: 'CUS-1001',
      fullName: 'Amina Khan',
      status: 'ACTIVE',
      createdAt: '2026-10-06T20:00:00Z',
    });
  }
});
