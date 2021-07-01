export interface RequestWillBeSentEvent {
    frameId: string;
    loaderId: string;
    documentURL: String;
    requestId: string;
    request: Record<string, any>;
    timestamp: number;
    type: string;
}

export interface ResponseReceivedEvent {
    frameId: string;
    loaderId: string;
    requestId: string;
    response: Record<string, any>;
    timestamp: number;
    type: string;
}