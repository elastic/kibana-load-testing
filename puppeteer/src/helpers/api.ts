
import axios from 'axios'
import { Config } from '../types/config'
import chalk from 'chalk'

export async function loadSampleData(options: Config) {
    const defaultHeaders = {
        "content-type": "application/json",
        "kbn-version": options.version,
        "sec-fetch-mode": "cors",
        "sec-fetch-site": "same-origin"
    }

    const provider = options.baseUrl.includes('localhost') ? 'basic' : 'cloud-basic'
    const authBody = `{\"providerType\":\"basic\",\"providerName\":\"${provider}\",\"currentURL\":\"${options.baseUrl}/login?next=%2F\",\"params\":{\"username\":\"${options.username}\",\"password\":\"${options.password}\"}}`;
    const authResponse = await axios.post(options.baseUrl + "/internal/security/login", authBody, { headers: defaultHeaders })
    const cookie = (authResponse.headers['set-cookie'][0] as string).split(';')[0]
    const headers = { ...defaultHeaders, cookie }
    const ingestResponse = await axios.post(options.baseUrl + "/api/sample_data/ecommerce", null, { headers })
    if (ingestResponse.status != 200) {
        throw Error(chalk.red(`Failed to load sample data: '${ingestResponse.statusText}'`))
    }
}