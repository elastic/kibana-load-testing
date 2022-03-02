
import axios, { AxiosResponse, AxiosError } from 'axios'
import { Config } from '../types/config'
import chalk from 'chalk'

async function post(url: string, body: string | null, headers: any, errorMsg?: string): Promise<AxiosResponse<any>> {
    try {
        return await axios.post(url, body, headers)
    } catch (error) {
        const err = error as AxiosError
        console.log(chalk.red(err?.message));
        console.log(err?.response);
        throw Error();
    }
}

export async function loadSampleData(options: Config) {
    const defaultHeaders = {
        "content-type": "application/json",
        "kbn-version": options.version,
        "sec-fetch-mode": "cors",
        "sec-fetch-site": "same-origin"
    }

    const provider = options.baseUrl.includes('localhost') ? 'basic' : 'cloud-basic'
    const authBody = `{\"providerType\":\"basic\",\"providerName\":\"${provider}\",\"currentURL\":\"${options.baseUrl}/login?next=%2F\",\"params\":{\"username\":\"${options.username}\",\"password\":\"${options.password}\"}}`;
    const authResponse = await post(
        options.baseUrl + "/internal/security/login",
        authBody,
        { headers: defaultHeaders },
        'Failed to login'
    )
    const setCookieHeaders = authResponse.headers['set-cookie'];
    if (setCookieHeaders) {
        const cookie = setCookieHeaders[0].toString().split(';')[0];
        const headers = { ...defaultHeaders, cookie };
        const ingestResponse = await post(
            options.baseUrl + '/api/sample_data/ecommerce',
            null,
            { headers },
            'Failed to load sample data'
        )
        if (ingestResponse.status != 200) {
            throw Error(chalk.red(`Failed to load sample data: '${ingestResponse.statusText}'`));
        }
    } else throw Error(chalk.red(`Failed to auth in Kibana, can't read 'set-cookie'`))
}