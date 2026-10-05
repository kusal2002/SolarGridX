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

The API URL should include the `/api` path.

The dashboard routes are `/users` and `/stations`. The available route depends on the authenticated user's role.

Authentication determines which dashboard route is available.

## Adding components

To add components to your app, run the following command:

Use the existing component conventions when adding new UI pieces.

```bash
npx shadcn@latest add button
```

This will place the ui components in the `src/components` directory.

## Using components

To use the components in your app, import them as follows:

Components can be imported through the configured `@` path alias.

```tsx
import { Button } from "@/components/ui/button"
```

Run the development server from the `Client` directory.

Use `npm run dev` for local development.
