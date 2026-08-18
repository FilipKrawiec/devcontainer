package devcli.ui

import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.github.ajalt.mordant.terminal.Terminal

object Theme {
    val term = Terminal()

    // Icons
    val iconSuccess: String = green("✔")
    val iconFailure: String = red("✖")
    val iconWarning: String = yellow("⚠")
    val iconInfo: String = cyan("ℹ")
    val iconStep: String = magenta("❯")
    val iconBullet: String = gray("•")

    // Semantic text helpers
    fun success(text: String): String = green(text)
    fun danger(text: String): String = red(text)
    fun warning(text: String): String = yellow(text)
    fun info(text: String): String = cyan(text)
    fun muted(text: String): String = gray(text)
    fun boldText(text: String): String = bold(text)
    fun highlight(text: String): String = (bold + brightWhite)(text)
    fun code(text: String): String = (bold + brightCyan)(text)

    // Status badges
    fun statusBadge(text: String, isOk: Boolean): String {
        return if (isOk) {
            (bold + green)("[$text]")
        } else {
            (bold + red)("[$text]")
        }
    }

    fun stalenessBadge(staleness: String): String {
        return when {
            staleness == "Up to date" -> green(staleness)
            staleness.startsWith("Ahead") -> yellow(staleness)
            staleness.startsWith("Behind") -> red(staleness)
            staleness.startsWith("Diverged") -> magenta(staleness)
            staleness == "No upstream" -> gray(staleness)
            else -> gray(staleness)
        }
    }

    fun branchBadge(headRef: String): String {
        return when {
            headRef.endsWith("*") -> yellow(headRef)
            headRef == "HEAD" || headRef.length == 7 -> magenta(headRef)
            headRef == "error" || headRef == "invalid" -> red(headRef)
            else -> cyan(headRef)
        }
    }
}
