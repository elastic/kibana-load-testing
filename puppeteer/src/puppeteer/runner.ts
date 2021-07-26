import puppeteer from 'puppeteer';
import { Config } from '../types/config'
import { isNotResource, isJSONString } from '../helpers/helpers'
import { ResponseReceivedEvent, RequestWillBeSentEvent } from '../types/event'
import { Request } from '../types/request'
import { resolve } from 'path';

export async function runner(scenarioFiles: string[], options: Config) {
    let runFailed = false;
    const scenarioResponses: Map<string, Map<string, Request>> = new Map();
    let browserArgs: any = { args: ['--no-sandbox', '--disable-setuid-sandbox'] }
    const headless = options.headless || false;
    if (!headless) {
        browserArgs['headless'] = headless;
    }
    console.log(`Starting puppeteer: ${JSON.stringify(browserArgs)}`);
    const browser = await puppeteer.launch(browserArgs);
    for (let i = 0; i < scenarioFiles.length; i++) {
        const frameRequests = new Map<string, Request>();
        const page = await browser.newPage();
        const client = await page.target().createCDPSession();

        page.on('response', async(response) => {
            const requestEvent = response.request();
            const url = requestEvent.url();
            if (url.startsWith(options.baseUrl) && isNotResource(url) && frameRequests.has(requestEvent._requestId)) {
                let request = frameRequests.get(requestEvent._requestId);
                const contentType = requestEvent?.headers()['content-type'];
                if (request && contentType && contentType.indexOf('json') > -1) {
                    const text = await response.text();
                    if (isJSONString(text)) {
                        request.responseBody = text;
                    }
                }
            }
        })

        client.on('Network.requestWillBeSent', (event: RequestWillBeSentEvent) => {
            const url = event.request['url'] as string
            if (url.startsWith(options.baseUrl) && isNotResource(url)) {
                //console.log(`${event.requestId} ${url}`)
                const request: Request = {
                    frameId: event.frameId,
                    loaderId: event.loaderId,
                    method: event.request['method'] as string,
                    requestUrl: event.request['url'] as string,
                    requestHeaders: event.request['headers'] as Record<string, any>,
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
            console.log(`----------------Running ${scenarioFiles[i]}---------------`)
            const { run } = await import(resolve(__dirname, '..', 'scenario', scenarioFiles[i]));
            await run(options, page);
        } catch (err) {
            console.log(err)
            runFailed = true;
        } finally {
            await page.close();
            scenarioResponses.set(scenarioFiles[i], frameRequests);
            console.log(`----------------Finished---------------`)
            if (runFailed) {
                throw Error();
            }
        }
    }

    await browser.close();

    return scenarioResponses;
}