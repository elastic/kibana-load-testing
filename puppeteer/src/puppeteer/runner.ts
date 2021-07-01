import puppeteer from 'puppeteer';
import { Config } from '../types/config'
import { isNotResource } from '../helpers/helpers'
import { ResponseReceivedEvent, RequestWillBeSentEvent } from '../types/event'
import { Request } from '../types/request'
import { resolve } from 'path';

export async function runner(scenarioFiles: string[], options: Config) {
    const scenarioResponses: Map<string, Map<string, Request>> = new Map();
    const browser = await puppeteer.launch({ headless: false });// args: ['--no-sandbox']
    for (let i = 0; i < scenarioFiles.length; i++) {
        const frameRequests = new Map<string, Request>();
        const page = await browser.newPage();
        const client = await page.target().createCDPSession();

        client.on('Network.requestWillBeSent', (event: RequestWillBeSentEvent) => {
            const url = event.request['url'] as string
            if (url.startsWith(options.baseUrl) && isNotResource(url)) {
                console.log(`${event.requestId} ${url}`)
                const request: Request = {
                    frameId: event.frameId,
                    loaderId: event.loaderId,
                    method: event.request['method'] as string,
                    requestUrl: event.request['url'] as string,
                    requestHeaders: event.request['headers'] as Record<string, any>
                }
                if (event.request['hasPostData'] == true) {
                    request.postData = event.request['postData'] as string
                }
                frameRequests.set(event.requestId, request)
            }
        });

        client.on('Network.responseReceived', (event: ResponseReceivedEvent) => {
            const url = event.response['url'] as string
            if (url.startsWith(options.baseUrl) && isNotResource(url) && frameRequests.has(event.requestId)) {
                let request = frameRequests.get(event.requestId)
                if (request) {
                    request.responseUrl = event.response['url'] as string
                    request.responseHeaders = event.response['headers'] as Record<string, any>
                    request.status = event.response['status'] as number
                    request.statusText = event.response['statusText'] as string
                    request.responseTime = event.response['responseTime'] as number
                }
            }
        });

        await client.send('Network.enable');
        try {
            const { run } = await import(resolve(__dirname, '..', 'scenario', scenarioFiles[i]));
            await run(options, page);
        } catch (err) {
            console.log(err)
        } finally {
            await page.close();
            scenarioResponses.set(scenarioFiles[i], frameRequests);
        }
    }

    await browser.close();

    return scenarioResponses;
}