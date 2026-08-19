# Wokini — odświeżona strona internetowa

Ten projekt zawiera nową wersję statycznej strony `wokini.pl` przygotowaną pod:

- nowoczesny, responsywny layout,
- podstawy SEO i dostępności,
- obsługę zgód cookies (niezbędne / analityczne / marketingowe),
- osobne podstrony polityk prywatności i cookies.

## Uruchomienie lokalne

```bash
python3 -m http.server 8000
```

Następnie otwórz: `http://localhost:8000`.

## Ważne przed publikacją

1. Uzupełnij dane firmy w `privacy.html`.
2. Zweryfikuj treści polityk z prawnikiem.
3. Podłącz analitykę/marketing dopiero po uzyskaniu zgody użytkownika.
