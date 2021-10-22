import { HTTPRequest, Page } from 'puppeteer';

interface ResolvableRequest extends HTTPRequest {
  pendingResolver?: () => void;
}

export class PendingRequests {
  page: Page;

  resourceTypes: string[];

  pendingRequests: Set<HTTPRequest>;

  finishedWithSuccess: Set<HTTPRequest>;

  finishedWithErrors: Set<HTTPRequest>;

  promisees: Promise<void>[];

  requestListener: (request: ResolvableRequest) => void;

  requestFailedListener: (request: ResolvableRequest) => void;

  requestFinishedListener: (request: ResolvableRequest) => void;

  constructor(page: Page, resourceTypes = ['document', 'xhr', 'fetch']) {
    this.promisees = [];
    this.page = page;
    this.resourceTypes = resourceTypes;
    this.pendingRequests = new Set();
    this.finishedWithSuccess = new Set();
    this.finishedWithErrors = new Set();

    this.requestListener = (request: ResolvableRequest) => {
      if (this.resourceTypes.includes(request.resourceType())) {
        this.pendingRequests.add(request);
        this.promisees.push(
          new Promise(resolve => {
            request.pendingResolver = resolve;
          }),
        );
      }
    };

    this.requestFailedListener = (request: ResolvableRequest) => {
      if (this.resourceTypes.includes(request.resourceType())) {
        this.pendingRequests.delete(request);
        this.finishedWithErrors.add(request);
        if (request.pendingResolver) {
          request.pendingResolver();
        }
        delete request.pendingResolver;
      }
    };

    this.requestFinishedListener = (request: ResolvableRequest) => {
      if (this.resourceTypes.includes(request.resourceType())) {
        this.pendingRequests.delete(request);
        this.finishedWithSuccess.add(request);
        if (request.pendingResolver) {
          request.pendingResolver();
        }
        delete request.pendingResolver;
      }
    };

    page.on('request', this.requestListener);
    page.on('requestfailed', this.requestFailedListener);
    page.on('requestfinished', this.requestFinishedListener);
  }

  removePageListeners() {
    this.page.off('request', this.requestListener);
    this.page.off('requestfailed', this.requestFailedListener);
    this.page.off('requestfinished', this.requestFinishedListener);
  }

  async waitForAllXhrFinished() {
    if (this.pendingXhrCount() === 0) {
      return;
    }
    await Promise.all(this.promisees);
  }

  async waitOnceForAllXhrFinished() {
    await this.waitForAllXhrFinished();
    await new Promise((resolve) => {
      setTimeout(resolve, 3000);
    });
    await this.waitForAllXhrFinished();

    this.removePageListeners();
  }

  pendingXhrCount() {
    return this.pendingRequests.size;
  }

  stop() {
    this.removePageListeners();
  }
}