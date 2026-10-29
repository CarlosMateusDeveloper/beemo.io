FROM golang:1.23-alpine AS build
WORKDIR /build
COPY agenda-service/go.mod agenda-service/go.sum ./
RUN go mod download
COPY agenda-service/ ./
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/agenda .

FROM alpine:3.21
COPY --from=build /out/agenda /app/agenda
USER 10001:10001
EXPOSE 8081
ENTRYPOINT ["/app/agenda"]
