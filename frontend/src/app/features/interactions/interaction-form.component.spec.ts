import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Interaction } from './interaction.model';
import { InteractionFormComponent } from './interaction-form.component';

describe('InteractionFormComponent', () => {
  let fixture: ComponentFixture<InteractionFormComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InteractionFormComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(InteractionFormComponent);
    fixture.componentRef.setInput('customerId', 'CUS-1001');
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('sends the trimmed interaction, emits the saved response, and clears the summary', async () => {
    await fixture.whenStable();
    let saved: Interaction | undefined;
    fixture.componentInstance.saved.subscribe((interaction) => (saved = interaction));
    const summary: HTMLTextAreaElement = fixture.nativeElement.querySelector(
      '[data-testid="interaction-summary"]',
    );
    summary.value = '  Follow up on billing  ';
    summary.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    const request = http.expectOne('/api/v1/interactions');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      customerId: 'CUS-1001',
      interactionType: 'NOTE',
      summary: 'Follow up on billing',
    });
    const response = {
      id: 'INT-3',
      customerId: 'CUS-1001',
      interactionType: 'NOTE',
      summary: 'Follow up on billing',
      correlationId: 'correlation-3',
      createdAt: '2026-10-09T15:00:00Z',
    };
    request.flush(response, { status: 201, statusText: 'Created' });
    fixture.detectChanges();
    await fixture.whenStable();

    expect(saved).toEqual(response);
    expect(summary.value).toBe('');
  });

  it('shows a required error and does not submit a blank summary', async () => {
    await fixture.whenStable();
    const summary: HTMLTextAreaElement = fixture.nativeElement.querySelector(
      '[data-testid="interaction-summary"]',
    );
    summary.value = '   ';
    summary.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    const error: HTMLElement = fixture.nativeElement.querySelector(
      '[data-testid="interaction-summary-error"]',
    );
    expect(error.textContent?.trim()).toBe('Summary is required.');
    expect(error.getAttribute('role')).toBe('alert');
    http.expectNone('/api/v1/interactions');
  });

  it('shows a form error and keeps the summary when the API rejects it', async () => {
    await fixture.whenStable();
    const summary: HTMLTextAreaElement = fixture.nativeElement.querySelector(
      '[data-testid="interaction-summary"]',
    );
    summary.value = 'Needs correction';
    summary.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    http
      .expectOne('/api/v1/interactions')
      .flush(null, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    const error: HTMLElement = fixture.nativeElement.querySelector(
      '[data-testid="interaction-form-error"]',
    );
    expect(error.textContent?.trim()).toBe(
      'The interaction was not saved. Check the summary and try again.',
    );
    expect(error.getAttribute('role')).toBe('alert');
    expect(summary.value).toBe('Needs correction');
  });

  it('cancels a pending request when destroyed', async () => {
    await fixture.whenStable();
    const summary: HTMLTextAreaElement = fixture.nativeElement.querySelector(
      '[data-testid="interaction-summary"]',
    );
    summary.value = 'Pending interaction';
    summary.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

    const request = http.expectOne('/api/v1/interactions');
    fixture.destroy();

    expect(request.cancelled).toBeTrue();
  });

  it('disables the submit button while the request is pending', async () => {
    await fixture.whenStable();
    const summary: HTMLTextAreaElement = fixture.nativeElement.querySelector(
      '[data-testid="interaction-summary"]',
    );
    summary.value = 'Pending interaction';
    summary.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    const button: HTMLButtonElement = fixture.nativeElement.querySelector(
      '[data-testid="interaction-submit"]',
    );
    expect(button.disabled).toBeTrue();
    expect(button.textContent).toContain('Saving…');

    http.expectOne('/api/v1/interactions').flush({
      id: 'INT-3',
      customerId: 'CUS-1001',
      interactionType: 'NOTE',
      summary: 'Pending interaction',
      correlationId: 'correlation-3',
      createdAt: '2026-10-09T15:00:00Z',
    });
  });
});
