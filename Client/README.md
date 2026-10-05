# React + TypeScript + Vite + shadcn/ui

This is a template for a new Vite project with React, TypeScript, and shadcn/ui.

The client is the browser-based interface for SolarGridX.

## Local configuration

Copy `.env.example` to `.env.local` and set the API URL:

Keep local environment files out of version control.

```bash
cp .env.example .env.local
```

The default local value is `http://127.0.0.1:5084/api`. Set `VITE_API_URL` to the deployed API URL for other environments.

The dashboard routes are `/users` and `/stations`. The available route depends on the authenticated user's role.

## Adding components

To add components to your app, run the following command:

```bash
npx shadcn@latest add button
```

This will place the ui components in the `src/components` directory.

## Using components

To use the components in your app, import them as follows:

```tsx
import { Button } from "@/components/ui/button"
```
