# Vérifications à faire

- [ ] Tarif utilisé pour les camions de type 2 (requête A)
- [ ] Arrondi ou coupure des centimes (requête B)
- [ ] Code de production de GenererFacture.aspx.vb (règle exacte du choix du tarif)
- [ ] Texte de dbo.SP_MOUVEMENT_LIST_ALL_BY_CRITERIONS_4_FACTURE (quels bons sont proposés à la facturation)
- [ ] Mécanisme NEW_RATE_APPLIED / OLD_RATE_ID_FACTURE (refacturation ?)
- [ ] Ce qui alimente dbo.Livraison_Acpacc depuis Sage 300
- [ ] Sens de « OT » et validité du tarif n° 15
- [ ] Validation métier : annulation limitée aux bons créés par la récupération