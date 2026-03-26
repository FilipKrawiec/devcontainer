FROM mcr.microsoft.com/devcontainers/base:ubuntu

ARG TARGETARCH
ARG NODE_VERSION=24.14.0
ARG PYTHON_VERSION=3.14.3
ARG GO_VERSION=1.25.5
ARG GRADLE_VERSION=9.4.0
ARG RUST_VERSION=1.86.0
ARG FLUTTER_VERSION=3.41.2

RUN apt-get update \
    && export DEBIAN_FRONTEND=noninteractive \
    && apt-get install -y --no-install-recommends \
        build-essential \
        ca-certificates \
        curl \
        file \
        git-lfs \
        git \
        gpg \
        jq \
        libglu1-mesa \
        libstdc++6 \
        make \
        openssh-client \
        openjdk-25-jdk \
        python3 \
        python3-pip \
        python3-venv \
        ripgrep \
        unzip \
        xz-utils \
        zip \
    && case "${TARGETARCH}" in \
         amd64) NODE_ARCH="x64"; GO_ARCH="amd64"; UV_ARCH="x86_64" ;; \
         arm64) NODE_ARCH="arm64"; GO_ARCH="arm64"; UV_ARCH="aarch64" ;; \
         *) echo "unsupported TARGETARCH: ${TARGETARCH}" >&2; exit 1 ;; \
       esac \
    && curl -fsSL "https://nodejs.org/download/release/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-${NODE_ARCH}.tar.xz" -o /tmp/node.tar.xz \
    && tar -xJf /tmp/node.tar.xz -C /usr/local --strip-components=1 \
    && rm -f /tmp/node.tar.xz \
    && curl -LsSf https://astral.sh/uv/install.sh | env UV_UNMANAGED_INSTALL=/usr/local/bin sh \
    && uv python install "${PYTHON_VERSION}" \
    && ln -sf "/root/.local/share/uv/python/cpython-${PYTHON_VERSION}-linux-${UV_ARCH}-gnu/bin/python3" /usr/local/bin/python3 \
    && ln -sf /usr/local/bin/python3 /usr/local/bin/python \
    && npm install -g pnpm \
    && curl -fsSL "https://go.dev/dl/go${GO_VERSION}.linux-${GO_ARCH}.tar.gz" -o /tmp/go.tgz \
    && rm -rf /usr/local/go \
    && tar -C /usr/local -xzf /tmp/go.tgz \
    && rm -f /tmp/go.tgz \
    && curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o /tmp/gradle.zip \
    && unzip -q /tmp/gradle.zip -d /opt \
    && ln -s "/opt/gradle-${GRADLE_VERSION}/bin/gradle" /usr/local/bin/gradle \
    && rm -f /tmp/gradle.zip

RUN rm -rf /root/.cargo /root/.rustup \
    && case "${TARGETARCH}" in \
         amd64) RUST_TRIPLE="x86_64-unknown-linux-gnu" ;; \
         arm64) RUST_TRIPLE="aarch64-unknown-linux-gnu" ;; \
         *) echo "unsupported TARGETARCH: ${TARGETARCH}" >&2; exit 1 ;; \
       esac \
    && curl https://sh.rustup.rs -sSf | sh -s -- -y --profile minimal \
    && /root/.cargo/bin/rustup toolchain install "${RUST_VERSION}-${RUST_TRIPLE}" --profile minimal \
    && /root/.cargo/bin/rustup default "${RUST_VERSION}-${RUST_TRIPLE}"

RUN git clone --depth 1 --branch "${FLUTTER_VERSION}" https://github.com/flutter/flutter.git /opt/flutter \
    && /opt/flutter/bin/flutter config --no-analytics \
    && /opt/flutter/bin/flutter precache --linux \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

ENV OLLAMA_HOST=http://host.docker.internal:11434
ENV JAVA_HOME=/usr/lib/jvm/java-25-openjdk
ENV GRADLE_USER_HOME=/home/vscode/.gradle
ENV CARGO_HOME=/home/vscode/.cargo
ENV RUSTUP_HOME=/home/vscode/.rustup
ENV FLUTTER_HOME=/opt/flutter
ENV PATH=/root/.local/bin:/home/vscode/.local/bin:/home/vscode/.cargo/bin:/root/.cargo/bin:/usr/local/go/bin:/opt/flutter/bin:${PATH}
