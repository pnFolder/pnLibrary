package org.pnfolder.pnlibrary.core.link

/** What a game server may send to the proxy once its signature has checked out. */
sealed interface LinkFrame {
    /** A server report. */
    class Report(val report: LinkReport) : LinkFrame

    /** A remote update request for another server or the proxy. */
    class Request(val request: RemoteRequest) : LinkFrame

    /** The answer to a remote update request. */
    class Result(val result: RemoteResult) : LinkFrame

    /** A data question for another server or the proxy. */
    class Call(val call: DataCall) : LinkFrame

    /** One piece of the answer to a data question. */
    class Chunk(val chunk: DataChunk) : LinkFrame
}
