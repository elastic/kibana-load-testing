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
    if (typeof options.headless !== 'undefined' && options.headless === false) {
        browserArgs['headless'] = false;
    }
    for (let i = 0; i < scenarioFiles.length; i++) {
        console.log(`Starting puppeteer: ${JSON.stringify(browserArgs)}`);
        const browser = await puppeteer.launch(browserArgs);
        const frameRequests = new Map<string, Request>();
        const page = await browser.newPage();
        const client = await page.target().createCDPSession();

        page.on('response', async(response) => {
            if (response) {
                const status = response?.status();
                if (status && (status >= 300) && (status <=399)) {
                    // console.log(`Redirect from ${response?.url()} to ${response?.headers()?.location}`)
                } else {
                    let requestEvent: puppeteer.HTTPRequest | undefined;
                    try {
                        requestEvent = response?.request();
                    } catch (error) {
                        console.error(`onResponse: failed to get HTTPRequest event`)
                    }
                    if (requestEvent) {
                        let requestId: string | undefined;
                        try {
                            requestId = requestEvent?._requestId
                        } catch (error) {
                            console.error(`onResponse: failed to get requestId from HTTPRequest event`)
                        }
                        if (requestId && frameRequests.has(requestId)) {
                            let request = frameRequests.get(requestId);
                            if (request && requestEvent?.headers()?.['content-type']?.indexOf('json') > -1) {
                                try {
                                    const text = await response?.text();
                                    if (isJSONString(text)) {
                                        request.responseBody = text;
                                    }
                                } catch(error) {
                                    console.error(`onResponse: failed to get response.text() from HTTPRequest event`)
                                }
                                
                            }
                        }
                    }
                }
            }
        })

        client.on('Network.requestWillBeSent', (event: RequestWillBeSentEvent) => {
            const url = event?.request?.url as string
            try {
                const requestId = event.requestId || ''
                if (requestId.length > 0 && url.startsWith(options.baseUrl) && isNotResource(url)) {
                    //console.log(`requestWillBeSent: ${event.requestId} ${url}`)
                    const request: Request = {
                        frameId: event?.frameId,
                        loaderId: event?.loaderId,
                        method: event?.request?.method as string,
                        requestUrl: event?.request?.url as string,
                        requestHeaders: event?.request?.headers as Record<string, any>,
                    }
                    if (event?.request?.hasPostData == true) {
                        request.postData = event?.request?.postData as string
                    }
                    frameRequests.set(requestId, request)
                }
            } catch(error) {
                console.log(`requestWillBeSent: failed to create Request for ${url}, skipping`)
            }
        });

        client.on('Network.responseReceived', (event: ResponseReceivedEvent) => {
            const url = event.response['url'] as string
            if (url.startsWith(options.baseUrl) && isNotResource(url) && frameRequests.has(event?.requestId)) {
                //console.log(`responseReceived: ${event.requestId} ${url}`)
                let request = frameRequests.get(event?.requestId)
                if (request) {
                    request.responseUrl = event?.response?.url as string
                    request.responseHeaders = event?.response?.headers as Record<string, any>
                    request.status = event?.response?.status as number
                    request.statusText = event?.response?.statusText as string
                    request.responseTime = event?.response?.responseTime as number
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
        // close browser after each scenario
        await browser.close();
    }

    return scenarioResponses;
}