/** @type {import('tailwindcss').Config} */
export default {
    // Pfade zu allen Dateien, die Tailwind-Klassen enthalten
    content: process.env.NODE_ENV === 'production'
      ? [
        './index.html',
        './src/**/*.{vue,js,ts,jsx,tsx}'
    ]
    : [ './src/**/*.{js,ts}' ]
  ,
    theme: {
        extend: {
            colors: {
                // Definition einer Primärfarbe (Indigo), die wir im Projekt nutzen
                primary: {
                    50: '#f5f7ff',
                    100: '#ebf0fe',
                    200: '#ced9fb',
                    300: '#a3b5f7',
                    400: '#7086f0',
                    500: '#4f46e5', // Hauptfarbe
                    600: '#4338ca',
                    700: '#3730a3',
                }
            },
            fontFamily: {
                // Moderne serifenlose Schriftart
                sans: ['Inter', 'ui-sans-serif', 'system-ui', '-apple-system', 'sans-serif'],
            },
        },
    },
    plugins: [
        // Das Forms-Plugin sorgt für schicke Checkboxen und Inputs
        require('@tailwindcss/forms'),
    ],
}
