/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Electronic mail: epistemereader@gmail.com
 */
package com.aryan.reader.paginatedreader


object UserAgentStylesheet {
    val default: String = """
        /* Basic inline formatting */
        b, strong {
            font-weight: bold;
        }
        i, em, cite, dfn {
            font-style: italic;
        }
        u {
            text-decoration: underline;
        }
        s, strike, del {
            text-decoration: line-through;
        }
        code, kbd, samp, tt, pre {
            font-family: monospace;
        }

        /* Basic block elements */
        h1 {
            font-size: 2em;
            font-weight: bold;
            margin-top: 0.67em;
            margin-bottom: 0.67em;
        }
        h2 {
            font-size: 1.5em;
            font-weight: bold;
            margin-top: 0.83em;
            margin-bottom: 0.83em;
        }
        h3 {
            font-size: 1.17em;
            font-weight: bold;
            margin-top: 1em;
            margin-bottom: 1em;
        }
        h4 {
            font-size: 1em;
            font-weight: bold;
            margin-top: 1.33em;
            margin-bottom: 1.33em;
        }
        h5 {
            font-size: 0.83em;
            font-weight: bold;
            margin-top: 1.67em;
            margin-bottom: 1.67em;
        }
        h6 {
            font-size: 0.67em;
            font-weight: bold;
            margin-top: 2.33em;
            margin-bottom: 2.33em;
        }
        p {
            margin-top: 1em;
            margin-bottom: 1em;
        }
        div {
            margin-top: 0;
            margin-bottom: 0;
        }
        blockquote {
            margin-top: 1em;
            margin-bottom: 1em;
            margin-left: 40px;
            margin-right: 40px;
        }
        dl {
            margin-top: 1em;
            margin-bottom: 1em;
        }
        dt {
            font-weight: bold;
        }
        dd {
            margin-left: 40px;
        }
        ul, ol {
            margin-top: 1em;
            margin-bottom: 1em;
            padding-left: 40px;
        }
        li {
            margin-top: 0.5em;
            margin-bottom: 0.5em;
        }
        hr {
            margin-top: 0.5em;
            margin-bottom: 0.5em;
        }
    """.trimIndent()
}