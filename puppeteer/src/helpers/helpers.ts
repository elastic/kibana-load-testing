export function isNotResource(url: string) {
    return !(/(js|css|woff2|jpeg|png|svg|json|ttf)$/.test(url))
}

export function mapToJSON(map: Map<string, any>) {
    let jsonObject: any = {};
    map.forEach((value, key) => {
        jsonObject[key] = value
    });
    return <JSON>jsonObject
}

export function isJSONString(str: string) {
    try {
        JSON.parse(str);
    } catch (e) {
        return false;
    }
    return true;
}

export function strToJSON(str: string | undefined) {
    if (!str) return {}
    return isJSONString(str) ? JSON.parse(str) : {}
}
