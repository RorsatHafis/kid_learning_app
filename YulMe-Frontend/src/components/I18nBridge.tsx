import { useEffect } from 'react';

type Dict = Record<string, unknown>;
function flatten(value: Dict, out: Record<string,string> = {}) {
  for (const [k,v] of Object.entries(value)) {
    if (v && typeof v === 'object' && !Array.isArray(v)) flatten(v as Dict,out);
    else if (typeof v === 'string') out[v]=v;
  }
  return out;
}

export default function I18nBridge(){
  useEffect(()=>{
    const lang=localStorage.getItem('yulme.language')||'en';
    if(lang==='en') return;
    let active=true;
    fetch(`/i18n/${lang}.json`).then(r=>r.json()).then((dict:Dict)=>{
      if(!active) return;
      const pairs:Record<string,string>={};
      const walk=(v:unknown)=>{if(v&&typeof v==='object'&&!Array.isArray(v)){for(const [k,x] of Object.entries(v as Dict)){if(typeof x==='string'){const en=knownEnglish[k]; if(en)pairs[en]=x;} else walk(x)}}};
      walk(dict);
      const translate=()=>{
        const walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);
        const nodes:Text[]=[]; let n; while(n=walker.nextNode()) nodes.push(n as Text);
        for(const node of nodes){const text=node.nodeValue?.trim(); if(text&&pairs[text]) node.nodeValue=node.nodeValue!.replace(text,pairs[text]);}
      };
      const observer=new MutationObserver(()=>{observer.disconnect();translate();observer.observe(document.body,{subtree:true,childList:true,characterData:true});});
      translate(); observer.observe(document.body,{subtree:true,childList:true,characterData:true});
    }).catch(()=>undefined);
    return ()=>{active=false};
  },[]);
  return null;
}

// English source values keyed by the translation JSON's leaf keys. This keeps the
// JSON files authoritative while allowing existing screens to localize without
// duplicating a translation library dependency in the MVP.
const knownEnglish:Record<string,string>={
  home:'Home',learn:'Learn',progress:'My Progress',parent:'Parent Insight',settings:'Settings',signIn:'Sign in',signOut:'Sign out',getStarted:'Get started',continue:'Continue',saveChanges:'Save changes',tryAgain:'Try again',backHome:'Back home',goHome:'Go home',open:'Open',start:'Start',next:'Next',checkAnswer:'Check answer',nextQuestion:'Next question',finishActivity:'Finish activity',pleaseWait:'Please wait…',loading:'Loading…',account:'Account',admin:'Admin',teacher:'Teacher',principal:'Principal',parentRole:'Parent',learner:'Learner',active:'Active',score:'score',dayStreak:'day streak',activities:'activities',learningTime:'learning time',howItWorks:'How it works',forParents:'For parents',whyYulMe:'Why YulMe',startLearning:'Start learning',alreadyAccount:'I already have an account',kidFirst:'Kid-first design',adaptivePractice:'Adaptive practice',parentInsights:'Parent insights',personalized:'Personalized',confidenceBuilding:'Confidence-building',progressYouCanSee:'Progress you can see',signInTo:'Sign in',chooseWorkspace:'Choose your workspace.'
};
