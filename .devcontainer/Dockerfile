FROM mcr.microsoft.com/devcontainers/base:ubuntu

ARG TARGETARCH
ARG NODE_VERSION=24.14.0
ARG PYTHON_VERSION=3.14.3
ARG GO_VERSION=1.25.5
ARG GRADLE_VERSION=9.4.0
ARG RUST_VERSION=1.86.0
ARG FLUTTER_VERSION=3.41.2
ARG GRAALVM_VERSION=21.0.2

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
        python3 \
        python3-pip \
        python3-venv \
        ripgrep \
        unzip \
        xz-utils \
        zip \
    # Install GitHub CLI (gh)
    && mkdir -p /etc/apt/keyrings \
    && curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg | gpg --dearmor -o /etc/apt/keyrings/githubcli-archive-keyring.gpg \
    && chmod 644 /etc/apt/keyrings/githubcli-archive-keyring.gpg \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" > /etc/apt/sources.list.d/github-cli.list \
    && apt-get update && apt-get install -y gh \
    && case "${TARGETARCH}" in \
         amd64) NODE_ARCH="x64"; GO_ARCH="amd64"; UV_ARCH="x86_64"; GRAAL_ARCH="x64" ;; \
         arm64) NODE_ARCH="arm64"; GO_ARCH="arm64"; UV_ARCH="aarch64"; GRAAL_ARCH="aarch64" ;; \
         *) echo "unsupported TARGETARCH: ${TARGETARCH}" >&2; exit 1 ;; \
       esac \
    # Install Node.js
    && curl -fsSL "https://nodejs.org/download/release/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-${NODE_ARCH}.tar.xz" -o /tmp/node.tar.xz \
    && tar -xJf /tmp/node.tar.xz -C /usr/local --strip-components=1 \
    && rm -f /tmp/node.tar.xz \
    # Install GraalVM JDK
    && curl -fsSL "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-${GRAALVM_VERSION}/graalvm-community-jdk-${GRAALVM_VERSION}_linux-${GRAAL_ARCH}_bin.tar.gz" -o /tmp/graalvm.tgz \
    && mkdir -p /opt/graalvm \
    && tar -xzf /tmp/graalvm.tgz -C /opt/graalvm --strip-components=1 \
    && rm -f /tmp/graalvm.tgz \
    # Install Python (uv)
    && curl -LsSf https://astral.sh/uv/install.sh | env UV_UNMANAGED_INSTALL=/usr/local/bin sh \
    && uv python install "${PYTHON_VERSION}" \
    && ln -sf "/root/.local/share/uv/python/cpython-${PYTHON_VERSION}-linux-${UV_ARCH}-gnu/bin/python3" /usr/local/bin/python3 \
    && ln -sf /usr/local/bin/python3 /usr/local/bin/python \
    # Install pnpm, agy CLI, codex CLI, and claude-code CLI
    && npm install -g pnpm @anthropic-ai/claude-code @google/antigravity-cli codex-cli || true \
    # Install Go
    && curl -fsSL "https://go.dev/dl/go${GO_VERSION}.linux-${GO_ARCH}.tar.gz" -o /tmp/go.tgz \
    && rm -rf /usr/local/go \
    && tar -C /usr/local -xzf /tmp/go.tgz \
    && rm -f /tmp/go.tgz \
    # Install Gradle
    && curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o /tmp/gradle.zip \
    && unzip -q /tmp/gradle.zip -d /opt \
    && ln -s "/opt/gradle-${GRADLE_VERSION}/bin/gradle" /usr/local/bin/gradle \
    && rm -f /tmp/gradle.zip

# Pre-populate SSH known hosts for GitLab, GitHub, Bitbucket
RUN mkdir -p /etc/ssh && ssh-keyscan gitlab.com github.com bitbucket.org >> /etc/ssh/ssh_known_hosts 2>/dev/null || true

# Ensure non-root docker socket access for vscode user
RUN groupadd -f docker && usermod -aG docker vscode

RUN rm -rf /root/.cargo /root/.rustup \
    && case "${TARGETARCH}" in \
         amd64) RUST_TRIPLE="x86_64-unknown-linux-gnu" ;; \
         arm64) RUST_TRIPLE="aarch64-unknown-linux-gnu" ;; \
         *) echo "unsupported TARGETARCH: arm64" >&2; exit 1 ;; \
       esac \
    && curl https://sh.rustup.rs -sSf | sh -s -- -y --profile minimal \
    && /root/.cargo/bin/rustup toolchain install "${RUST_VERSION}-${RUST_TRIPLE}" --profile minimal \
    && /root/.cargo/bin/rustup default "${RUST_VERSION}-${RUST_TRIPLE}"

RUN git clone --depth 1 --branch "${FLUTTER_VERSION}" https://github.com/flutter/flutter.git /opt/flutter \
    && /opt/flutter/bin/flutter config --no-analytics \
    && /opt/flutter/bin/flutter precache --linux \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Copy Kotlin Clikt dev-cli source from .devcontainer/cli and compile into /usr/local/bin/dev
ENV JAVA_HOME=/opt/graalvm
ENV GRAALVM_HOME=/opt/graalvm
COPY cli /opt/dev-cli
RUN cd /opt/dev-cli && /opt/gradle-9.4.0/bin/gradle installDist \
    && ln -sf /opt/dev-cli/build/install/dev-cli/bin/dev-cli /usr/local/bin/dev

# Configure shell autocompletion for dev CLI and set /projects volume permissions
RUN mkdir -p /projects /home/vscode \
    && echo 'eval "$(dev --generate-completion bash 2>/dev/null)"' >> /etc/bash.bashrc \
    && echo 'eval "$(dev --generate-completion zsh 2>/dev/null)"' >> /etc/zsh/zshrc \
    && echo 'eval "$(dev --generate-completion bash 2>/dev/null)"' >> /home/vscode/.bashrc \
    && echo 'eval "$(dev --generate-completion zsh 2>/dev/null)"' >> /home/vscode/.zshrc \
    && chown -R vscode:vscode /projects /home/vscode

# Environment variables
ENV OLLAMA_HOST=http://host.docker.internal:11434
ENV ANTHROPIC_BASE_URL=http://host.docker.internal:11434/v1
ENV ANTHROPIC_API_KEY=ollama
ENV OPENAI_BASE_URL=http://host.docker.internal:11434/v1
ENV OPENAI_API_KEY=ollama

ENV GRADLE_USER_HOME=/home/vscode/.gradle
ENV CARGO_HOME=/home/vscode/.cargo
ENV RUSTUP_HOME=/home/vscode/.rustup
ENV FLUTTER_HOME=/opt/flutter
ENV PATH=/opt/graalvm/bin:/root/.local/bin:/home/vscode/.local/bin:/home/vscode/.cargo/bin:/root/.cargo/bin:/usr/local/go/bin:/opt/flutter/bin:${PATH}
