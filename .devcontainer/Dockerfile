FROM mcr.microsoft.com/devcontainers/base:ubuntu@sha256:bb779775cfc7d2e65f9b0f80f17770878f5a50c00d15cc873b627c5c214ce37e

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

ARG TARGETARCH
ARG NODE_VERSION=24.14.0
ARG PYTHON_VERSION=3.14.3
ARG GO_VERSION=1.25.5
ARG GRADLE_VERSION=9.4.0
ARG RUST_VERSION=1.86.0
ARG FLUTTER_VERSION=3.41.2
ARG GRAALVM_VERSION=21.0.2

# Early Environment setup to ensure PATH contains local bin directories during build
ENV JAVA_HOME=/opt/graalvm
ENV GRAALVM_HOME=/opt/graalvm
ENV PNPM_HOME=/home/vscode/.local/share/pnpm
ENV NPM_CONFIG_STORE_DIR=/home/vscode/.local/share/pnpm/store
ENV PNPM_STORE_DIR=/home/vscode/.local/share/pnpm/store
ENV UV_CACHE_DIR=/home/vscode/.cache/uv
ENV GOPATH=/home/vscode/go
ENV GOCACHE=/home/vscode/.cache/go-build
ENV PUB_CACHE=/home/vscode/.pub-cache
ENV GRADLE_USER_HOME=/home/vscode/.gradle
ENV RUSTUP_HOME=/opt/rustup
ENV UV_PYTHON_INSTALL_DIR=/opt/uv/python
ENV FLUTTER_HOME=/opt/flutter
ENV FLUTTER_SUPPRESS_ANALYTICS=true
ENV PATH=/home/vscode/.local/share/pnpm:/opt/graalvm/bin:/home/vscode/.local/bin:/opt/cargo/bin:/usr/local/go/bin:/opt/flutter/bin:${PATH}

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
        zsh \
        zsh-autosuggestions \
        zsh-syntax-highlighting \
        zstd \
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
    && ln -sf "/opt/uv/python/cpython-${PYTHON_VERSION}-linux-${UV_ARCH}-gnu/bin/python3" /usr/local/bin/python3 \
    && ln -sf /usr/local/bin/python3 /usr/local/bin/python \
    # Install pnpm and OpenAI Codex CLI
    && npm install -g pnpm @openai/codex \
    # Install Claude Code CLI via official install script using bash
    && curl -fsSL https://claude.ai/install.sh | bash \
    && (cp /root/.local/bin/claude /usr/local/bin/claude 2>/dev/null || true) \
    # Install Google Antigravity CLI (agy) into /usr/local/bin
    && curl -fsSL https://antigravity.google/cli/install.sh | bash -s -- --dir /usr/local/bin \
    && (cp /root/.local/bin/agy /usr/local/bin/agy 2>/dev/null || true) \
    && chmod +x /usr/local/bin/claude /usr/local/bin/agy 2>/dev/null || true \
    # Install official Ollama Linux CLI (enables 'ollama launch claude', etc.)
    && curl -fsSL https://ollama.com/install.sh | sh \
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

# Set zsh as the default shell for the vscode user. Docker access is configured only in an explicit privileged profile.
RUN chsh -s /bin/zsh vscode

RUN case "${TARGETARCH}" in \
         amd64) RUST_TRIPLE="x86_64-unknown-linux-gnu" ;; \
         arm64) RUST_TRIPLE="aarch64-unknown-linux-gnu" ;; \
         *) echo "unsupported TARGETARCH: arm64" >&2; exit 1 ;; \
       esac \
    && CARGO_HOME=/opt/cargo RUSTUP_HOME=/opt/rustup curl --fail --show-error --silent https://sh.rustup.rs | CARGO_HOME=/opt/cargo RUSTUP_HOME=/opt/rustup sh -s -- -y --profile minimal --default-toolchain "${RUST_VERSION}-${RUST_TRIPLE}" --no-modify-path \
    && chmod -R a+rX /opt/cargo /opt/rustup

RUN git clone --depth 1 --branch "${FLUTTER_VERSION}" https://github.com/flutter/flutter.git /opt/flutter \
    && /opt/flutter/bin/flutter config --no-analytics \
    && /opt/flutter/bin/flutter precache --linux \
    && chmod -R a+rwX /opt/flutter \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Copy Kotlin Clikt dev-cli source from .devcontainer/cli and compile into /usr/local/bin/dev
COPY cli /opt/dev-cli
RUN cd /opt/dev-cli && /opt/gradle-${GRADLE_VERSION}/bin/gradle installDist \
    && ln -sf /opt/dev-cli/build/install/dev-cli/bin/dev-cli /usr/local/bin/dev

# Configure image-native Zsh prompt (One Dark colors), autocompletion, and pnpm store config
RUN mkdir -p /projects /home/vscode /home/vscode/.local/share/pnpm /home/vscode/.cache \
    && echo 'store-dir=/home/vscode/.local/share/pnpm/store' > /etc/pnpmrc \
    && echo 'eval "$(dev --generate-completion bash 2>/dev/null)"' >> /etc/bash.bashrc \
    && echo 'eval "$(dev --generate-completion zsh 2>/dev/null)"' >> /etc/zsh/zshrc \
    && echo 'autoload -U colors && colors 2>/dev/null || true' >> /etc/zsh/zshrc \
    && echo 'PROMPT="%F{blue}%~%f %F{green}\$(git branch --show-current 2>/dev/null)%f %F{magenta}❯%f "' >> /etc/zsh/zshrc \
    && echo 'source /usr/share/zsh-autosuggestions/zsh-autosuggestions.zsh 2>/dev/null || true' >> /etc/zsh/zshrc \
    && echo 'source /usr/share/zsh-syntax-highlighting/zsh-syntax-highlighting.zsh 2>/dev/null || true' >> /etc/zsh/zshrc \
    && chown -R vscode:vscode /projects /home/vscode
