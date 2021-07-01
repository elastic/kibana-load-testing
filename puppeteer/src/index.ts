#!/usr/bin/env node

import { scenarioRunner } from './runner';
import yargs = require("yargs");
import { isWebUri } from 'valid-url'
import chalk from 'chalk'
import fs from 'fs'
import { resolve } from 'path';

interface Config {
    baseUrl: string;
    username: string;
    password: string;
    version: string;
}


process.on('unhandledRejection', error => {
    console.log(error);
});

(async function run() {
    let config: Config;
    let configPath = resolve('./config.json');
    console.log(configPath)
    if (fs.existsSync(configPath)) {
        console.log('config.json found')
        // using local config file
        config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
    } else {
        console.log('config.json not found, reading args')
        const argv = await yargs
            .version(false)
            .usage("Usage: --baseUrl http://localhost:5620 --username elastic --password changeme --version 8.0.0")
            .option("baseUrl", { alias: "baseUrl", describe: "Kibana base url", type: "string", demandOption: true })
            .option("username", { alias: "username", describe: "Kibana username", type: "string", demandOption: true })
            .option("password", { alias: "password", describe: "Kibana password", type: "string", demandOption: true })
            .option("version", { alias: "version", describe: "Kibana version", type: "string", demandOption: true })
            .argv;
        const { baseUrl, username, password, version } = argv
        config = { baseUrl, username, password, version }
    }

    if (!isWebUri(config.baseUrl)) {
        throw new Error(chalk.red(`Invalid 'baseUrl'=${config.baseUrl}, valid example: 'http://localhost:5620'`));
    }
    if (!(/(7|8).\d{1,2}.\d/.test(config.version))) {
        throw new Error(chalk.red(`Invalid 'version'=${config.version}, valid example: '7.12.1'`));
    }

    await scenarioRunner(config)
})()
