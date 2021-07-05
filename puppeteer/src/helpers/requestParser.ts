import { Request } from '../types/request'

export function getRequestsSequence(dataSet: Map<string, Request>, baseUrl: string) {
    const output: Map<string, Array<string>> = new Map();
    const loaderPathMapper: Map<string, string> = new Map();

    dataSet.forEach((request, requestId) => {
        if (/[A-Z0-9]{32}/.test(requestId)) {
            // new loader
            let path = request.requestUrl.replace(baseUrl, '')
            loaderPathMapper.set(requestId, path)
        } else {
            let path = loaderPathMapper.get(request.loaderId) || ''
            let array = output.get(path) || []
            array.push(request.requestUrl.replace(baseUrl, ''))
            output.set(path, array)
        }
    });

    return output;
}

export function getDuplicates(dataSet: Map<string, Request>, baseUrl: string) {
    console.log(`Checking for duplicates:`)
    const loaderPathMapper: Map<string, string> = new Map();
    const output: Map<string, Map<string, Request>> = new Map();
    dataSet.forEach((request, requestId) => {
        if (/[A-Z0-9]{32}/.test(requestId)) {
            // new loader
            let path = request.requestUrl.replace(baseUrl, '');
            loaderPathMapper.set(requestId, path);
            output.set(path, new Map<string, Request>());
        } else {
            let path = loaderPathMapper.get(request.loaderId) || ''
            let map = output.get(path) as Map<string, Request>
            map.set(requestId, request)
        }
    })

    output.forEach((reqMap: Map<string, Request>, path: string) => {
        const unique = new Map<string, string>();
        const dup = []

        reqMap.forEach((request: Request, requestId: string) => {
            if (unique.has(toObjCompare(request))) {
                console.log(`Found duplicate ${request.requestUrl}`)
            } else {
                unique.set(toObjCompare(request), requestId)
            }
        })
    });

    console.log(`----------------Finished---------------`)
}

function toObjCompare(request: Request) {
    return JSON.stringify({
        loaderId: request.loaderId,
        frameId: request.frameId,
        method: request.method,
        url: request.requestUrl,
        headers: request.requestHeaders,
        payload: request.postData
    })
}

function areEqual(req1: Request, req2: Request) {
    return toObjCompare(req1) === toObjCompare(req2);
}