import fs from 'fs';
import { resolve } from 'path';
import { Request } from '../types/request';
import { compareWithBaseline } from './compare';
import { mapToJSON, isJSONString, strToJSON } from './helpers';
import { getRequestsSequence, sortByPlugin } from './requestParser';


function writeToFile(filePath: string, data: object | string) {
    const str = typeof data == 'object' ? JSON.stringify(data, null, 4) : data
    fs.writeFileSync(filePath, str);
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

    const sorted = sortByPlugin(requests, baseUrl)

    writeToFile(resolve(resultRootDir, 'requests.json'), mapToJSON(actualSequence));
    // save individual requests
    sorted.forEach((requests, pluginName) => {
        const pluginDir = resolve(requestsDir, pluginName);
        fs.mkdirSync(pluginDir,  { recursive: true });
        requests.forEach((request, index) => {
            const obj = {
                loaderId: request.loaderId,
                frameId: request.frameId,
                method: request.method,
                requestUrl: request.requestUrl,
                requestHeader: request.requestHeaders,
                postData: strToJSON(request.postData),
                responseUrl: request.responseUrl || '',
                responseHeaders: request.responseHeaders,
                status: request.status || 0,
                statusText: request.statusText,
                responseTime: request.responseTime,
                responseBody: strToJSON(request.responseBody)
            }
            writeToFile(resolve(pluginDir, `${index}.json`), obj)
        });
    });

    //compare sequence with baseline
    const { isNewRequestFound, verifiedSequence } = compareWithBaseline(scenario, actualSequence);
    writeToFile(resolve(resultRootDir, 'requests_verified.json'), mapToJSON(verifiedSequence));

    return isNewRequestFound;
}