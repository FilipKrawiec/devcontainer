# syntax=docker/dockerfile:1.4
ARG BASE_IMAGE=mcr.microsoft.com/devcontainers/base:ubuntu@sha256:bb779775cfc7d2e65f9b0f80f17770878f5a50c00d15cc873b627c5c214ce37e

ARG TARGETARCH
ARG NODE_VERSION=26.7.0
ARG PYTHON_VERSION=3.14.7
ARG GO_VERSION=1.26.6
ARG GRADLE_VERSION=9.7.0
ARG RUST_VERSION=1.97.1
ARG FLUTTER_VERSION=3.47.0
ARG GRAALVM_VERSION=25.0.2

# -----------------------------------------------------------------------------
# Stage: Fetch & Unpack Node.js
# -----------------------------------------------------------------------------
FROM ${BASE_IMAGE} AS fetch-node
ARG TARGETARCH
ARG NODE_VERSION
RUN ARCH="${TARGETARCH:-$(dpkg --print-architecture)}" \
    && case "${ARCH}" in \
         amd64|x86_64) NODE_ARCH="x64" ;; \
         arm64|aarch64) NODE_ARCH="arm64" ;; \
         *) echo "unsupported architecture: ${ARCH}" >&2; exit 1 ;; \
       esac \
    && mkdir -p /target/usr/local \
    && curl -fsSL "https://nodejs.org/download/release/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-${NODE_ARCH}.tar.xz" \
       | tar -xJf - -C /target/usr/local --strip-components=1

# -----------------------------------------------------------------------------
# Stage: Fetch & Unpack Go
# -----------------------------------------------------------------------------
FROM ${BASE_IMAGE} AS fetch-go
ARG TARGETARCH
ARG GO_VERSION
RUN ARCH="${TARGETARCH:-$(dpkg --print-architecture)}" \
    && case "${ARCH}" in \
         amd64|x86_64) GO_ARCH="amd64" ;; \
         arm64|aarch64) GO_ARCH="arm64" ;; \
         *) echo "unsupported architecture: ${ARCH}" >&2; exit 1 ;; \
       esac \
    && mkdir -p /target/usr/local/go \
    && curl -fsSL "https://go.dev/dl/go${GO_VERSION}.linux-${GO_ARCH}.tar.gz" \
       | tar -C /target/usr/local/go --strip-components=1 -xzf -

# -----------------------------------------------------------------------------
# Stage: Fetch & Unpack GraalVM JDK
# -----------------------------------------------------------------------------
FROM ${BASE_IMAGE} AS fetch-graalvm
ARG TARGETARCH
ARG GRAALVM_VERSION
RUN ARCH="${TARGETARCH:-$(dpkg --print-architecture)}" \
    && case "${ARCH}" in \
         amd64|x86_64) GRAAL_ARCH="x64" ;; \
         arm64|aarch64) GRAAL_ARCH="aarch64" ;; \
         *) echo "unsupported architecture: ${ARCH}" >&2; exit 1 ;; \
       esac \
    && mkdir -p /target/opt/graalvm \
    && curl -fsSL "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-${GRAALVM_VERSION}/graalvm-community-jdk-${GRAALVM_VERSION}_linux-${GRAAL_ARCH}_bin.tar.gz" \
       | tar -xzf - -C /target/opt/graalvm --strip-components=1

# -----------------------------------------------------------------------------
# Stage: Fetch & Unpack Gradle
# -----------------------------------------------------------------------------
FROM ${BASE_IMAGE} AS fetch-gradle
ARG GRADLE_VERSION
RUN apt-get update && apt-get install -y --no-install-recommends unzip curl ca-certificates \
    && mkdir -p /target/opt \
    && curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o /tmp/gradle.zip \
    && unzip -q /tmp/gradle.zip -d /target/opt \
    && rm -f /tmp/gradle.zip

# -----------------------------------------------------------------------------
# Stage: Install Python via uv
# -----------------------------------------------------------------------------
FROM ${BASE_IMAGE} AS fetch-python
ARG TARGETARCH
ARG PYTHON_VERSION
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && ARCH="${TARGETARCH:-$(dpkg --print-architecture)}" \
    && case "${ARCH}" in \
         amd64|x86_64) UV_ARCH="x86_64" ;; \
         arm64|aarch64) UV_ARCH="aarch64" ;; \
         *) echo "unsupported architecture: ${ARCH}" >&2; exit 1 ;; \
       esac \
    && curl -LsSf https://astral.sh/uv/install.sh | env UV_UNMANAGED_INSTALL=/target/usr/local/bin sh \
    && UV_PYTHON_INSTALL_DIR=/target/opt/uv/python /target/usr/local/bin/uv python install "${PYTHON_VERSION}" \
    && ln -sf "/opt/uv/python/cpython-${PYTHON_VERSION}-linux-${UV_ARCH}-gnu/bin/python3" /target/usr/local/bin/python3 \
    && ln -sf /usr/local/bin/python3 /target/usr/local/bin/python

# -----------------------------------------------------------------------------
# Stage: Install Rust & Cargo Toolchain
# -----------------------------------------------------------------------------
FROM ${BASE_IMAGE} AS fetch-rust
ARG TARGETARCH
ARG RUST_VERSION
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && ARCH="${TARGETARCH:-$(dpkg --print-architecture)}" \
    && case "${ARCH}" in \
         amd64|x86_64) RUST_TRIPLE="x86_64-unknown-linux-gnu" ;; \
         arm64|aarch64) RUST_TRIPLE="aarch64-unknown-linux-gnu" ;; \
         *) echo "unsupported architecture: ${ARCH}" >&2; exit 1 ;; \
       esac \
    && CARGO_HOME=/target/opt/cargo RUSTUP_HOME=/target/opt/rustup curl --fail --show-error --silent https://sh.rustup.rs \
       | CARGO_HOME=/target/opt/cargo RUSTUP_HOME=/target/opt/rustup sh -s -- -y --profile minimal --default-toolchain "${RUST_VERSION}-${RUST_TRIPLE}" --no-modify-path \
    && chmod -R a+rX /target/opt/cargo /target/opt/rustup

# -----------------------------------------------------------------------------
# Stage: Clone & Precache Flutter
# -----------------------------------------------------------------------------
FROM ${BASE_IMAGE} AS fetch-flutter
ARG FLUTTER_VERSION
RUN apt-get update && apt-get install -y --no-install-recommends git ca-certificates curl unzip \
    && git clone --depth 1 --branch "${FLUTTER_VERSION}" https://github.com/flutter/flutter.git /target/opt/flutter \
    && /target/opt/flutter/bin/flutter config --no-analytics \
    && /target/opt/flutter/bin/flutter precache --linux \
    && chmod -R a+rwX /target/opt/flutter

# -----------------------------------------------------------------------------
# Stage: Build Kotlin Dev CLI (`dev`)
# -----------------------------------------------------------------------------
FROM ${BASE_IMAGE} AS builder-cli
ARG GRADLE_VERSION
COPY --from=fetch-graalvm /target/opt/graalvm /opt/graalvm
COPY --from=fetch-gradle /target/opt/gradle-${GRADLE_VERSION} /opt/gradle-${GRADLE_VERSION}
ENV JAVA_HOME=/opt/graalvm
ENV PATH=/opt/graalvm/bin:/opt/gradle-${GRADLE_VERSION}/bin:${PATH}
COPY cli /opt/dev-cli
RUN cd /opt/dev-cli && gradle installDist

# -----------------------------------------------------------------------------
# Final Image Assembly
# -----------------------------------------------------------------------------
FROM ${BASE_IMAGE}

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

ARG GRADLE_VERSION=9.7.0

# Environment setup
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

# 1. Base OS Packages & System Repositories (rarely invalidated)
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
        just \
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
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# 2. Configure Shell, Defaults, and User Workspace Directories
RUN chsh -s /bin/zsh vscode \
    && mkdir -p /projects /home/vscode /home/vscode/.local/share/pnpm /home/vscode/.cache \
    && echo 'store-dir=/home/vscode/.local/share/pnpm/store' > /etc/pnpmrc \
    && echo 'eval "$(dev --generate-completion bash 2>/dev/null)"' >> /etc/bash.bashrc \
    && echo 'eval "$(dev --generate-completion zsh 2>/dev/null)"' >> /etc/zsh/zshrc \
    && echo 'autoload -U colors && colors 2>/dev/null || true' >> /etc/zsh/zshrc \
    && echo 'setopt PROMPT_SUBST' >> /etc/zsh/zshrc \
    && echo 'PROMPT="%F{blue}%~%f %F{green}\$(git branch --show-current 2>/dev/null)%f %F{magenta}❯%f "' >> /etc/zsh/zshrc \
    && echo 'source /usr/share/zsh-autosuggestions/zsh-autosuggestions.zsh 2>/dev/null || true' >> /etc/zsh/zshrc \
    && echo 'source /usr/share/zsh-syntax-highlighting/zsh-syntax-highlighting.zsh 2>/dev/null || true' >> /etc/zsh/zshrc

# 3. Copy Pre-built/Extracted Toolchains from Independent Stages
COPY --from=fetch-node /target/usr/local/ /usr/local/
COPY --from=fetch-go /target/usr/local/go /usr/local/go
COPY --from=fetch-graalvm /target/opt/graalvm /opt/graalvm
COPY --from=fetch-gradle /target/opt/ /opt/
COPY --from=fetch-python /target/usr/local/ /usr/local/
COPY --from=fetch-python /target/opt/uv /opt/uv
COPY --from=fetch-rust /target/opt/cargo /opt/cargo
COPY --from=fetch-rust /target/opt/rustup /opt/rustup
COPY --from=fetch-flutter /target/opt/flutter /opt/flutter

# 4. Install Global CLI Tools (pnpm, Codex, Claude, AGY, Ollama, Gradle symlink)
RUN ln -sf "/opt/gradle-${GRADLE_VERSION}/bin/gradle" /usr/local/bin/gradle \
    # Install pnpm and OpenAI Codex CLI
    && npm install -g pnpm @openai/codex \
    # Install Claude Code CLI via official install script
    && curl -fsSL https://claude.ai/install.sh | bash \
    && (cp /root/.local/bin/claude /usr/local/bin/claude 2>/dev/null || true) \
    # Install Google Antigravity CLI (agy) into /usr/local/bin
    && curl -fsSL https://antigravity.google/cli/install.sh | bash -s -- --dir /usr/local/bin \
    && (cp /root/.local/bin/agy /usr/local/bin/agy 2>/dev/null || true) \
    && chmod +x /usr/local/bin/claude /usr/local/bin/agy 2>/dev/null || true \
    # Install official Ollama Linux CLI
    && curl -fsSL https://ollama.com/install.sh | sh

# 5. Copy & Link Compiled Kotlin Dev CLI (only invalidated when CLI source changes)
COPY --from=builder-cli /opt/dev-cli /opt/dev-cli
RUN ln -sf /opt/dev-cli/build/install/dev-cli/bin/dev-cli /usr/local/bin/dev \
    && chown -R vscode:vscode /projects /home/vscode /opt/dev-cli
