
let loadKeyState, connectionState;
let nextMessageIndex = 0

function never(){ throw new Exception() }
function pong(){
    send(getConnectionState(never).pongURL, {
        "X-r-connection": getConnectionKey(never),
        "X-r-location": location+""
    })
    console.log("pong")
}
function sessionKey(orDo){ return sessionStorage.getItem("sessionKey") || orDo() }
function getLoadKey(orDo){ return loadKeyState || orDo() }
function loadKeyForSession(){ return "loadKeyForSession-" + sessionKey(never) }
function getConnectionState(orDo){ return connectionState || orDo() }
function getConnectionKey(orDo){ return getConnectionState(orDo).key || orDo() }
function connect(data) {
    console.log("conn: "+data)
    const [key,pongURL] = data.split(" ")
    connectionState = { key, pongURL }
    sessionKey(() => sessionStorage.setItem("sessionKey", getConnectionKey(never)))
    getLoadKey(() => { loadKeyState = getConnectionKey(never) })
    localStorage.setItem(loadKeyForSession(), getLoadKey(never))
    window.onhashchange = () => pong()
    pong()
}
function ping(data) {
    console.log("ping: "+data)
    if(localStorage.getItem(loadKeyForSession()) !== getLoadKey(never)) { // tab was refreshed/duplicated
        sessionStorage.clear()
        location.reload()
    } else if(getConnectionKey(never) === data) pong() // was not reconnected
}
function send(url,inHeaders){
    const headers = {
        ...inHeaders,
        "X-r-session": sessionKey(never),
        "X-r-index": nextMessageIndex++
    }
    //todo: contron message delivery at server
    fetch((window.feedbackUrlPrefix||"")+url, {method:"post", headers})
    return headers
}
function relocateHash(data) { 
    document.location.href = "#"+data 
}
export default function(){
    const receivers = {connect,ping,relocateHash}
    return ({receivers,send}) 
}