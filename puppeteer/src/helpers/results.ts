import fs from 'fs'
import { resolve } from 'path';
import { Request } from '../types/request'
import { compareWithBaseline } from './compare';
import { mapToJSON } from './helpers';
import { getRequestsSequence } from './requestParser';


function writeToFile(filePath: string, data: object | string) {
    const str = typeof data == 'object' ? JSON.stringify(data, null, 4) : data
    fs.writeFile(filePath, str, function (err) {
        if (err) {
            console.log(err);
        }
    });
}

export function saveResults(scenario: string, requests: Map<string, Request>, baseUrl: string) {
    const resultRootDir = resolve(__dirname, `..`, `..`, `output`, `run_${Date.now()}`, scenario);
    const requestsDir = resolve(resultRootDir, 'requests');
    fs.mkdirSync(requestsDir, { recursive: true });
    // save raw requests sequence
    let rawOutput: any = {}
    requests.forEach((request, requestId) => {
        rawOutput[requestId] = `${requestId} ${request.loaderId} ${request.method} ${request.requestUrl}`
    })
    writeToFile(resolve(resultRootDir, 'raw_sequence.json'), rawOutput);
    // save requests split into route group to compare with baseline
    const actualSequence = getRequestsSequence(requests, baseUrl)

    writeToFile(resolve(resultRootDir, 'requests.json'), mapToJSON(actualSequence));
    // save individual requests
    requests.forEach((request, requestId) => {
        const obj = {
            loaderId: request.loaderId,
            frameId: request.frameId,
            method: request.method,
            requestUrl: request.requestUrl,
            requestHeader: request.requestHeaders,
            postData: request.postData || '',
            responseUrl: request.responseUrl || '',
            responseHeaders: request.responseHeaders,
            status: request.status || 0,
            statusText: request.statusText,
            responseTime: request.responseTime
        }
        writeToFile(resolve(requestsDir, `${requestId}.json`), obj)
    });

    //compare sequence with baseline
    const verifiedSequence = compareWithBaseline(scenario, actualSequence);
    writeToFile(resolve(resultRootDir, 'requests_verified.json'), mapToJSON(verifiedSequence));
}