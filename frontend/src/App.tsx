import { BrowserRouter, Navigate, Route, Routes } from "react-router"
import { Autorisation, RouteProtegee } from "@/auth/Protections"
import Coquille from "@/layout/Coquille"
import Connexion from "@/pages/Connexion"
import EnConstruction from "@/pages/EnConstruction"

export default function App() {
  return (
      <BrowserRouter>
        <Routes>
          <Route path="/connexion" element={<Connexion />} />
          <Route element={<RouteProtegee><Coquille /></RouteProtegee>}>
            <Route index element={<EnConstruction titre="Tableau de bord" />} />
            <Route path="factures" element={
              <Autorisation permission="FACTURES_CONSULTER"><EnConstruction titre="Factures" /></Autorisation>} />
            <Route path="generation" element={
              <Autorisation permission="FACTURES_GENERER"><EnConstruction titre="Génération" /></Autorisation>} />
            <Route path="accpac" element={
              <Autorisation permission="ACCPAC_CONSULTER"><EnConstruction titre="ACCPAC" /></Autorisation>} />
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