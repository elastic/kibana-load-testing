export interface Request {
    frameId: string;
    loaderId: string;
    //requestId: string;
    method: string;
    requestUrl: string;
    postData?: string;
    requestHeaders: Record<string, any>;
    responseUrl?: string;
    responseHeaders?: Record<string, any>;
    status?: number;
    statusText?: string;
    responseTime?: number;
}