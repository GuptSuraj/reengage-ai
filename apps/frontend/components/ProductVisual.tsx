export function ProductVisual({kind="headphones"}:{kind?:string}) {
  return <div className={`product-visual ${kind}`} aria-hidden="true"/>;
}
