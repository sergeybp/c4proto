
export function mergeAll(list){
    function merge(to,from){
        Object.keys(from).forEach(key=>{
            if(!to[key]) to[key] = from[key]
            else if(to[key].constructor===Object && from[key].constructor===Object)
                merge(to[key],from[key])
            else throw ["unable to merge",to[key],from[key]]
        })
    }
    const to = {}
    list.forEach(from=>merge(to,from))
    return to
}

export const chain = args => state => args.reduce((st,f) => f(st), state)

export const transformNested = (name,inner) => state => {
    const was = state[name]
    const will = inner(was)
    return was===will ? state : {...state, [name]: will}
}

export const addSend = (url,inHeaders) => state => {
    const lastMessageIndex = (state.lastMessageIndex||0) + 1
    const headers = {...inHeaders, "X-r-index": lastMessageIndex}
    const prev = state.toSend || []
    const toSend = [...prev,{url, headers}]
    return ({...state, lastMessageIndex, toSend})
}