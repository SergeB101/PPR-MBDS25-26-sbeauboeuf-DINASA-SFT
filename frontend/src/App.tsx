import { BrowserRouter, Navigate, Route, Routes } from "react-router"
import { Autorisation, RouteProtegee } from "@/auth/Protections"
import Coquille from "@/layout/Coquille"
import Connexion from "@/pages/Connexion"
import EnConstruction from "@/pages/EnConstruction"
import TableauDeBord from "@/features/tableau-de-bord/TableauDeBord"
import ListeFactures from "@/features/factures/ListeFactures"
import PageFacture from "@/features/factures/PageFacture"
import PageGeneration from "@/features/generation/PageGeneration"
import PageAccpac from "@/features/accpac/PageAccpac"

export default function App() {
  return (
      <BrowserRouter>
        <Routes>
          <Route path="/connexion" element={<Connexion />} />
          <Route element={<RouteProtegee><Coquille /></RouteProtegee>}>
            <Route index element={
              <Autorisation permission="TABLEAU_DE_BORD_CONSULTER"><TableauDeBord /></Autorisation>} />
            <Route path="factures" element={
              <Autorisation permission="FACTURES_CONSULTER"><ListeFactures /></Autorisation>} />
            <Route path="factures/:id" element={
              <Autorisation permission="FACTURES_CONSULTER"><PageFacture /></Autorisation>} />
            <Route path="generation" element={
              <Autorisation permission="FACTURES_GENERER"><PageGeneration /></Autorisation>} />
            <Route path="accpac" element={
              <Autorisation permission="ACCPAC_CONSULTER"><PageAccpac /></Autorisation>} />
            <Route path="referentiel" element={
              <Autorisation permission="REFERENTIEL_CONSULTER"><EnConstruction titre="Référentiel" /></Autorisation>} />
            <Route path="acces" element={
              <Autorisation permission="SECURITE_ADMINISTRER"><EnConstruction titre="Gestion des accès" /></Autorisation>} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
  )
}