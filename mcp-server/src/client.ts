import fetch from "node-fetch";

const GAME_API_URL = process.env.GAME_API_URL || "http://localhost:8080";

export class GameApiClient {
    private token: string;

    constructor(token: string) {
        this.token = token;
    }

    async get(path: string): Promise<any> {
        const response = await fetch(`${GAME_API_URL}${path}`, {
            headers: { "Authorization": `Bearer ${this.token}` }
        });
        if (!response.ok) {
            const body = await response.text();
            throw new Error(`API error ${response.status}: ${body}`);
        }
        return response.json();
    }

    async post(path: string, body: any): Promise<any> {
        const response = await fetch(`${GAME_API_URL}${path}`, {
            method: "POST",
            headers: {
                "Authorization": `Bearer ${this.token}`,
                "Content-Type": "application/json"
            },
            body: JSON.stringify(body)
        });
        if (!response.ok) {
            const errorBody = await response.json().catch(() => ({})) as any;
            throw new Error(errorBody.error || `API error ${response.status}`);
        }
        return response.json();
    }

    async delete(path: string): Promise<any> {
        const response = await fetch(`${GAME_API_URL}${path}`, {
            method: "DELETE",
            headers: { "Authorization": `Bearer ${this.token}` }
        });
        if (!response.ok) throw new Error(`API error ${response.status}`);
        return response.json();
    }
}
