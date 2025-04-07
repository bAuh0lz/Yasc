/*
 * Copyright 2015 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.google.protobuf.ByteString;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import yasc.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class YascServer {

    private Server server;
    private int port;

    public void start() {
        try {
            _start();
        } catch (IOException e) {
            Main.api.logging().logToError("error while starting", e);
//            throw new RuntimeException(e);
        }
    }

    void _start() throws IOException {
        for (port = 5001; port <= 5101; port++) {
            try {
                server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", port))
                        .addService(new YascImpl())
//                        .addService(ProtoReflectionServiceV1.newInstance())
                        .build()
                        .start();
                break;
            } catch (IOException e) {
                continue;
            }
        }

        Main.api.logging().logToOutput("Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                Main.api.logging().logToError("*** shutting down gRPC server since JVM is shutting down");
                try {
                    YascServer.this.stop();
                } catch (InterruptedException e) {
                    e.printStackTrace(System.err);
                }
                Main.api.logging().logToError("*** server shut down");
            }
        });
    }

    void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public int getPort() {
        return port;
    }

    /**
     * Main launches the server from the command line.
     */

    static class YascImpl extends YascGrpc.YascImplBase {

        @Override
        public void setScriptServerPort(SetScriptServerPortRequest req, StreamObserver<SetScriptServerPortReply> responseObserver) {
            var scriptServerPort = req.getPort();

            Main.api.logging().logToOutput("Script runner's port set to " + scriptServerPort);
            var success = Main.script.prepare(scriptServerPort);

            var reply = SetScriptServerPortReply.newBuilder()
                    .setSuccess(success)
                    .build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }

        @Override
        public void setScriptsDir(SetScriptsDirRequest req, StreamObserver<SetScriptsDirReply> responseObserver) {
            var scriptsDir = req.getScriptsDir();

            Main.script.setScriptDirectory(scriptsDir);

            var reply = SetScriptsDirReply.newBuilder()
                    .setSuccess(true)
                    .build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }

        @Override
        public void sendRawHttpRequest(RawHttpRequest req, StreamObserver<RawHttpResponse> responseObserver) {
            var baseUrl = req.getBaseUrl();
            var reqBytes = req.getRequest().toByteArray();
            var needsResponse = req.getNeedsResponse();

            var httpService = HttpService.httpService(baseUrl);
            var httpReq = HttpRequest.httpRequest(httpService, ByteArray.byteArray(reqBytes));

            var res = Main.api.http().sendRequest(httpReq).response();

            var replyBuilder = RawHttpResponse.newBuilder();
            if (needsResponse) {
                replyBuilder.setResponse(ByteString.copyFrom(res.toByteArray().getBytes()));
            }

            var reply = replyBuilder.build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }

        @Override
        public void sendParsedHttpRequest(ParsedHttpRequest req, StreamObserver<RawHttpResponse> responseObserver) {

            var url = req.getUrl();
            var method = req.getMethod().name();
            var headers = req.getHeadersList();
            var body = req.getBody();
            var needsResponse = req.getNeedsResponse();

            var httpReq = HttpRequest.httpRequestFromUrl(url);
            httpReq = httpReq.withMethod(method);
            for (var header : headers) {
                httpReq = httpReq.withHeader(header.getName(), header.getValue());
            }
            httpReq = httpReq.withBody(body);

            var res = Main.api.http().sendRequest(httpReq).response();

            var replyBuilder = RawHttpResponse.newBuilder();
            if (needsResponse) {
                replyBuilder.setResponse(ByteString.copyFrom(res.toByteArray().getBytes()));
            }

            var reply = replyBuilder.build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }

        @Override
        public void fetchSmtpMessages(FetchSmtpMessagesRequest req, StreamObserver<FetchSmtpMessagesResponse> responseObserver) {
            var index = req.getIndex();

            var smtpConversations = Main.collaborator.fetchSmtpMessages();

            if (index > -1) {
                smtpConversations = smtpConversations.subList(index, index + 1);
            }

            var reply = FetchSmtpMessagesResponse.newBuilder()
                    .addAllConversations(smtpConversations)
                    .build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
    }

}
