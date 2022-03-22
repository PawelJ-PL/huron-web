import { Pool } from "pg"
import { readFile } from "fs/promises"
import path from "path"

export const dbSeed = async (host: string, port: number, user: string, password: string, database: string) => {
    const sqlFile = await readFile(path.resolve(__dirname, "..", "..", "..", "..", "test_db", "test_data.sql"))
    const lines = sqlFile
        .toString()
        .split("\n\n")
        .filter((line) => !line.startsWith("--") && line.trim() !== "")

    const pool = new Pool({
        host,
        port,
        user,
        password,
        database,
    })

    for (let line of lines) {
        await pool.query(line)
    }

    const connection = await pool.connect()
}
